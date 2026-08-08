# Omagotchi Backend Development Summary - 2026-08-08

## 작업 개요

오늘 작업은 기존 Spring Boot 백엔드 구조를 유지하면서 Profile/Nickname, Community, WebSocket/STOMP, Redis Cohort Presence를 단계별로 구현한 것이다. 프론트엔드, friends/block, chat, pet/evolution, Presence visibility override는 구현하지 않았다.

## Profile / Nickname

- APIs
  - `GET /api/users/me/profile`
  - `PATCH /api/users/me/nickname`
- nickname policy
  - `user_characters.nickname` 재사용
  - trim 후 길이 2~12 검증
  - 현재 존재하지 않는 charset/profanity/uniqueness 규칙은 추가하지 않음
- profile aggregation
  - 완료된 유효 학습 기록만 집계
  - 출석 날짜는 distinct date 기준
  - streak는 오늘 또는 어제부터 역방향 계산
  - 누락된 숫자 값은 0 반환
- reused existing domain/data
  - User/Profile/UserSettings 엔티티나 테이블을 만들지 않음
  - 대표/current `UserCharacter` 정책 재사용
  - `game_characters`에 없는 `type`, `assetKey`는 nullable 값으로 반환

## Community

### Read

- posts schema
  - Flyway `V17__create_community_posts.sql`
  - `community_posts`는 `NOTICE` / `FREE`, `GLOBAL` / `COHORT`, pinned, soft delete 상태를 저장
- GLOBAL/COHORT visibility
  - GLOBAL 게시글은 인증 사용자에게 노출
  - COHORT 게시글은 해당 cohort의 ACTIVE membership이 있을 때만 노출
  - 가시성은 DB query 조건에서 처리하고 메모리 필터링을 하지 않음
- JPA/JPQL filtering/search/pagination/order
  - JPA query layer에서 type filter, title/content search, pagination, count 처리
  - 기본 정렬은 `pinned DESC`, `created_at DESC`, `id DESC`

### Write / Authorization

- create/update/delete/pin
  - `POST /api/community/posts`
  - `PATCH /api/community/posts/{postId}`
  - `DELETE /api/community/posts/{postId}`
  - `PATCH /api/community/posts/{postId}/pin`
- STUDENT / MENTOR / MANAGER / SYSTEM_ADMIN policy
  - STUDENT는 FREE 게시글 작성 가능
  - MENTOR, MANAGER는 ACTIVE membership이 있는 cohort에 COHORT NOTICE 작성 가능
  - SYSTEM_ADMIN은 GLOBAL NOTICE와 모든 cohort NOTICE 작성 가능
  - pin/unpin은 SYSTEM_ADMIN 전용
- server-side membership validation
  - request payload의 `userId`, `authorUserId`, `role`, admin flag를 신뢰하지 않음
  - JWT 사용자와 cohort membership을 서버에서 확인
- soft delete
  - 삭제는 게시글 soft delete 정책으로 처리

### Attachments

- attachment metadata
  - Flyway `V18__create_community_post_attachments.sql`
  - DB에는 storage key, original file name, content type, size, display order 등 메타데이터만 저장
  - binary file content는 DB에 저장하지 않음
- local filesystem storage abstraction
  - `CommunityAttachmentStorage` 인터페이스와 `LocalCommunityAttachmentStorage` 구현
  - 추후 S3 등으로 교체할 수 있게 community domain과 분리
- configurable storage root
  - `community.attachments.storage-root`
  - 기본값 `data/community-attachments`
- limits/types
  - 최대 5 files
  - 파일당 5MB
  - `jpg`, `jpeg`, `png`, `gif`
  - 실제 파일 header 기반 MIME: `image/jpeg`, `image/png`, `image/gif`
- safe generated storage keys
  - client filename을 저장 경로로 사용하지 않음
  - server-side UUID 기반 storage key 생성
  - path traversal, unsafe filename, extension/MIME mismatch, empty file 검증
- cleanup behavior
  - 파일 저장 후 metadata persistence가 실패하면 새로 저장된 파일을 정리
  - 게시글 삭제 시 attachment metadata와 저장 파일을 함께 정리

## WebSocket / STOMP

- `spring-boot-starter-websocket` 추가
- endpoint: `/ws`
- STOMP destination conventions
  - `/app/presence/heartbeat`
  - `/topic/cohorts/{cohortId}/presence`
  - `/user/queue/notifications`
- CONNECT JWT authentication
  - STOMP `Authorization: Bearer ...` header를 기존 `JwtDecoder`와 `JwtAuthenticationConverter`로 검증
- authenticated Principal
  - 검증된 `JwtAuthenticationToken`을 WebSocket Principal로 설정
- SUBSCRIBE authorization
  - 인증된 Principal 없이는 구독 거부
  - 지원하지 않는 destination 구독 거부
- cohort topic authorization
  - `/topic/cohorts/{cohortId}/presence`는 JWT 사용자에게 해당 cohort ACTIVE membership이 있을 때만 허용

## Redis Presence

- `spring-boot-starter-data-redis` 추가
- Redis keys
  - `realtime:session:{sessionId}` -> `userId`, `cohortId`, TTL
  - `presence:user:{userId}:sessions` -> active WebSocket session IDs
  - `presence:user:{userId}` -> `ONLINE` / `OFFLINE`
  - `presence:cohort:{cohortId}` -> currently online users
- 60s session TTL
  - `realtime.presence.session-ttl`
  - env: `REALTIME_PRESENCE_SESSION_TTL`
- CONNECT registration
  - JWT 사용자 기준으로 현재 ACTIVE cohort membership을 서버에서 찾고 Redis session/user/cohort 상태를 등록
- heartbeat
  - `/app/presence/heartbeat`
  - client payload 없이 WebSocket session과 JWT 사용자 기준으로 TTL refresh
- disconnect/expiry cleanup
  - disconnect event에서 해당 session 제거
  - snapshot 계산 시 만료된 session hash를 user session set에서 정리
- multi-session behavior
  - 하나의 유효 session이라도 남아 있으면 사용자 ONLINE 유지
  - 마지막 유효 session 제거/만료 시 OFFLINE 처리 및 cohort online set에서 제거
- ONLINE/OFFLINE
  - 이번 범위에서는 실제 전이 상태로 ONLINE/OFFLINE을 사용
  - `AWAY`, `BUSY`는 enum에만 준비되어 있고 별도 상태 전이는 구현하지 않음
- cohort snapshot/broadcast
  - CONNECT, disconnect, cleanup 후 `/topic/cohorts/{cohortId}/presence`로 snapshot broadcast
  - 초기 snapshot은 `GET /api/cohorts/me/presence`
- Presence != physical attendance
  - Presence는 Redis ephemeral WebSocket 상태이며 출석/학습 기록과 분리

## Framework / Library Usage

- Spring Security
  - REST와 WebSocket 모두 JWT 검증, `JwtAuthenticationToken`, role claim 변환 재사용
  - 사용자 식별은 server-side authentication에서만 수행
- Bean Validation
  - request DTO 입력 검증에 사용
  - nickname, community create/update payload 검증은 기존 validation/error convention을 따름
- Spring Data JPA
  - community post, attachment metadata, cohort membership 조회에 사용
- QueryDSL
  - 기존 study profile summary aggregation repository에서 재사용
- Flyway
  - `community_posts`, `community_post_attachments` schema 추가에 사용
- MultipartFile
  - image attachment upload 처리에 사용
  - raw multipart parsing은 직접 구현하지 않음
- Spring WebSocket/STOMP
  - endpoint, broker prefix, application destination, `ChannelInterceptor`, session disconnect event 사용
  - custom WebSocket protocol이나 raw frame parsing은 구현하지 않음
- Spring Data Redis
  - `StringRedisTemplate` hash/set/value/TTL 연산으로 ephemeral Presence 상태 저장
  - Redis `KEYS` 사용 없음
- Spring transaction/test support
  - application service transaction boundary 유지
  - focused unit/MVC/query/storage tests로 단계별 검증

## Database / Configuration Changes

- Flyway migrations
  - `V17__create_community_posts.sql`: community post read/write model
  - `V18__create_community_post_attachments.sql`: community attachment metadata
- `application.yaml`
  - `community.attachments.storage-root`
  - `community.attachments.max-file-size`
  - `community.attachments.max-count`
  - `community.attachments.allowed-extensions`
  - `community.attachments.allowed-content-types`
  - `realtime.presence.session-ttl`
- Dependencies
  - `spring-boot-starter-websocket`
  - `spring-boot-starter-data-redis`

## Security Decisions

- current user resolved server-side
  - REST: `JwtAuthenticationToken` -> `AuthenticatedUser`
  - WebSocket: STOMP CONNECT JWT -> `JwtAuthenticationToken` Principal
- client userId/role/admin flags not trusted
  - community author, role, admin permission, WebSocket user identity는 request payload에서 받지 않음
- cohort membership checked server-side
  - community visibility/write authorization
  - WebSocket cohort topic subscription
  - Redis Presence cohort resolution
- WebSocket subscription authorization
  - `/topic/cohorts/{cohortId}/presence`는 ACTIVE membership 필요
  - unsupported destination은 거부
- attachment path/file safety
  - generated storage key만 저장 경로로 사용
  - path traversal 차단
  - extension과 실제 MIME/header 검증
  - file count, file size, empty file 검증

## Tests

- Profile focused tests
  - `UserProfileServiceTest`
  - `UserProfileControllerTest`
- Community focused tests
  - `CommunityPostQueryServiceTest`
  - `CommunityPostQueryJpaAdapterTest`
  - `CommunityPostQueryJpaAdapterIT`
  - `CommunityPostControllerTest`
  - `CommunityPostCommandServiceTest`
  - `LocalCommunityAttachmentStorageTest`
- WebSocket/Presence focused tests
  - `WebSocketConnectAuthenticationInterceptorTest`
  - `WebSocketSubscribeAuthorizationInterceptorTest`
  - `CohortPresenceServiceTest`
  - `LearningSecurityMvcTest`
- Final documentation phase verification
  - `./mvnw test` attempted before the documentation commit
  - Result in this local environment: compile and non-container tests ran, but Testcontainers-based Spring context tests failed because Docker was unavailable

## Commits

- `6c17aad feat: add user profile summary and nickname update`
  - Profile summary and nickname update APIs
- `63f1705 feat: add community post read model`
  - Community post schema, list/detail, search/filter/pagination, visibility query
- `0665740 feat: add community post management`
  - Shared community write APIs and authorization policy
- `c833ac3 feat: add community post attachments`
  - Attachment metadata schema and local image storage
- `c7bc287 refactor: add common websocket infrastructure`
  - Spring WebSocket/STOMP foundation, CONNECT auth, SUBSCRIBE auth
- `df226dc feat: add redis cohort presence`
  - Redis-backed cohort Presence registration, heartbeat, cleanup, snapshot/broadcast

## Current Backend Flow

HTTP:

```text
Controller
-> Application Service
-> Repository / QueryDSL adapter
-> DB
```

WebSocket CONNECT:

```text
STOMP CONNECT
-> JWT authentication
-> authenticated Principal
-> active cohort resolution
-> Redis Presence registration
-> STOMP cohort broadcast
```

WebSocket SUBSCRIBE:

```text
STOMP SUBSCRIBE
-> authenticated Principal check
-> destination convention check
-> cohort ACTIVE membership authorization
-> subscription allowed/denied
```

Presence heartbeat:

```text
/app/presence/heartbeat
-> authenticated WebSocket Principal
-> Redis session lookup
-> cohort membership recheck
-> session TTL refresh
```

## Deferred Work

NOT implemented:

- frontend integration
- Presence visibility overrides
- friends/block
- DM/group chat
- pet system
- Redis pub/sub scale-out
- other future realtime notification features

## Current Status

The current planned backend scope through Redis Cohort Presence is complete. Phase 5 Presence visibility is intentionally deferred.
