# Learning Service E2E 전 작업 가이드

- 기준일: `2026-08-20`
- 대상 순서: 프로필 캐릭터 계약 → 커뮤니티 첨부 조회 → Presence 계약 → Gamification 내부 이벤트 → 출결 조회 개선 → API 계약 테스트/REST Docs
- Frontend 전달 문서: `Frontend-Learning-API-Integration-Handoff.md`, `Frontend-Learning-Integration-Task-Brief.md`

## 0. 현재 IntelliJ 빌드 오류 해결

화면의 `Attempt to recreate a file for type ... QAttendanceRecord` 오류는 Flyway가 아니라
QueryDSL Q 클래스 annotation processor가 같은 파일을 두 번 생성하려 해서 발생한다.
현재 Maven 기준 `./mvnw clean compile`은 정상 통과한다.

현재 상위 IntelliJ 프로젝트의 `.idea/compiler.xml`에는 동일한 annotation processor 목록이
두 번 들어가 있다. 다음 순서로 IDE 설정만 정리한다.

1. IntelliJ의 Maven 창에서 `Reload All Maven Projects`를 실행한다.
2. `Settings → Build, Execution, Deployment → Compiler → Annotation Processors`에서
   프로젝트가 만든 중복 profile을 제거하고 `Obtain processors from project classpath`를 사용한다.
3. `Settings → Build Tools → Maven → Runner`에서 IDE Build/Run을 Maven에 위임한다.
4. 계속 재현되면 IntelliJ를 종료한 뒤 상위 프로젝트의 `.idea/compiler.xml`을 백업 후 제거하고
   프로젝트를 `omagotchi-learning-service/pom.xml` 기준으로 다시 연다.
5. 터미널에서 `./mvnw clean compile`로 확인한다. 생성 위치는
   `target/generated-sources/annotations` 한 곳이어야 한다.

`target/generated-sources/annotations`를 Source Root와 별도의 annotation processor output으로
동시에 수동 등록하지 않는다. Flyway 오류라면 로그에 `FlywayValidateException`, migration version,
checksum 등이 표시되므로 이번 오류와 구분한다.

## 2. Profile 캐릭터 `type`, `assetKey`

Frontend의 실제 `characterId + colorId` 자산 구조를 기준으로 구현되었다.

- `game_characters.asset_key`: `study`, `debug`, `sprout`, `server`, `night`, `kid`,
  `caffeine`, `commit`
- `user_characters.color_id`: `original`을 포함한 Frontend 8개 색상
- Profile `type`: Frontend `characterId`
- Profile `assetKey`: 확장자를 제외한 이미지 상대 키

예시:

```text
type=night, colorId=original, assetKey=night/night
type=night, colorId=pistachio, assetKey=night/pistachio
```

Frontend는 `/images/characters/${assetKey}.png`로 정적 PNG를 찾는다. 기존 `V6`는 수정하지 않고
`V9__align_character_assets_with_frontend.sql`에서 캐릭터 8종과 색상 컬럼을 추가한다.

## 3. Community 첨부파일 다운로드

현재 저장 Port는 `store`, `delete`만 있고 상세 응답은 첨부 메타데이터만 제공한다. 구현 순서는
다음과 같다.

1. `CommunityAttachmentStorage`에 `load(storageKey)` 읽기 Port를 추가한다.
2. 로컬 구현은 기존 `targetPath()`의 root 이탈 검사를 그대로 거쳐 `Resource` 또는 stream을 반환한다.
3. Query service에서 `postId` 조회 권한을 먼저 검사하고, `attachmentId`가 해당 게시글 소속인지 확인한다.
4. 아래 인증 endpoint를 추가한다.

```http
GET /api/v1/community/posts/{postId}/attachments/{attachmentId}
```

5. 응답에는 저장 경로가 아니라 원본 파일명 기반 `Content-Disposition`, 저장된 `Content-Type`,
   정확한 `Content-Length`를 설정한다.
6. 다른 게시글의 attachment ID 접근, path traversal, 삭제 파일, 권한 없는 cohort 게시글을 테스트한다.

Frontend/BFF는 상세 응답의 `attachmentId`로 위 API를 호출해 byte stream을 그대로 relay한다.
브라우저에 로컬 `storageKey`나 파일시스템 경로를 노출하지 않는다.

## 4. Presence의 “계약 결정”

계약 결정은 단순히 DTO 필드를 추가하는 일이 아니라 다음 세 가지를 먼저 확정하는 것이다.

- 화면에 필요한 필드: `userId`, `status`, `nickname`, `characterAssetKey` 중 무엇인지
- 데이터 소유 서비스: 닉네임/캐릭터는 Learning, 실명/이메일은 Identity 중 누가 제공하는지
- REST 초기 snapshot과 STOMP 실시간 event가 같은 사용자 표시 필드를 갖는지

권장안은 Learning이 자신이 소유한 `nickname`, `characterAssetKey`만 보강하고, 실명/이메일이
필요하면 Frontend BFF가 Identity 결과와 `userId`로 조합하는 것이다. Presence event마다 Identity를
동기 호출하지 않는다. 확정 전 현재 계약은 `userId`, `status`만이며 Frontend는 사용자 ID 축약 또는
별도 캐시된 멤버 map으로 표시한다.

## 5. Gamification 이벤트 연결

현재 `/gamification/events/**`는 Browser가 직접 호출하면 출결/학습 완료를 임의로 발생시킬 수 있다.
프런트 공개 API가 아니라 Learning 내부 도메인 이벤트로 바꾸는 것이 목표다.

권장 흐름:

```text
AttendanceService.checkIn 성공
  -> AttendanceCheckedInEvent(userId, cohortId, attendanceId, occurredAt)
  -> AFTER_COMMIT listener
  -> DailyQuestService.handleAttendance(userId)

Study 완료 처리 성공
  -> StudyCompletedEvent(userId, studyRecordId, occurredAt)
  -> AFTER_COMMIT listener
  -> DailyQuestService.handleStudyCompleted(userId)
```

- Application 계층에는 event와 publisher Port를 두고 Spring publisher는 Infrastructure에서 구현한다.
- listener는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 실행한다.
- 중복 event에 대비해 `(eventType, sourceId)` 또는 quest progress update를 idempotent하게 만든다.
- `character-checked`가 단순 화면 열기라면 보상 조건으로 사용하지 않는다. 실제 사용자 action API로
  정의하거나 퀘스트 종류에서 제외한다.
- `llm-quest-completed`는 LLM/검증 서비스가 인증된 내부 호출이나 메시지로 발행하고 Browser가
  완료를 직접 선언하지 않게 한다.
- 내부 전환 후 `/events/**` Controller는 제거하거나 Gateway에서 외부 접근을 차단한다.

Frontend는 출석 또는 학습 완료 API 성공 후 `/events/**`를 추가 호출하지 않는다. 홈/퀘스트를
재조회해 갱신된 상태만 표시한다.

## 6. 출결 날짜 조회와 Pagination

현재 내 출결 조회는 전체 이력을 `List`로 반환하고 관리자 조회만 단일 `date`를 받는다. 다음 계약을
권장한다.

```http
GET /api/v1/cohorts/{cohortId}/attendance-records/me
    ?from=2026-08-01&to=2026-08-31&page=0&size=31
```

```json
{
  "items": [],
  "page": {
    "number": 0,
    "size": 31,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

구현 시 repository는 membership ID와 inclusive `attendanceDate between from and to` 조건에
`Pageable`을 적용하고 `attendanceDate DESC`를 고정한다. `from <= to`, 최대 조회 기간 366일,
`size <= 100`을 검증한다. 날짜를 생략할 때의 기본값은 최근 31일로 둔다.

관리자 일별 조회는 현재 `?date=` 계약을 유지할 수 있다. 기수 규모가 커지면 같은 Page 응답으로
바꾸되, active membership 전체와 출결 record를 결합해 미입실자도 화면에 나타나야 하는지는 별도
관리자 화면 계약으로 확정한다.

Frontend는 전체 이력을 받은 뒤 자체 pagination하지 않고 `from`, `to`, `page`, `size`를 BFF에
전달한다. `today`는 `from=to=오늘`로 조회할 수 있다.

## 7. API 계약 테스트와 REST Docs

현재 다음 계약 테스트가 실제 JWT를 사용해 응답 필드를 고정하고 REST Docs snippet을 생성한다.

- Profile 내 프로필
- Cohort 출결 정책
- Attendance 내 출결 목록
- Community 게시글 목록과 page 구조
- Gamification 캐릭터 마스터
- Presence 초기 snapshot

실행 방법:

```bash
./mvnw -Dtest=UserProfileControllerTest,CohortControllerTest,AttendanceControllerTest,CommunityPostControllerTest,GamificationControllerTest,PresenceControllerTest test
./mvnw -DskipTests prepare-package
```

결과물:

```text
target/generated-snippets/**
target/generated-docs/index.html
```

2~6번의 계약을 실제로 변경할 때 해당 Controller test와 `src/docs/asciidoc/index.adoc`을 함께
수정해야 한다. 그 다음 Frontend DTO/map test를 맞추고 마지막으로 Gateway를 포함한 E2E를 수행한다.
