# Learning Service

교육 기수·학습·출결·공간·팀·게이미피케이션 데이터 소유 서비스.

## 담당 범위

- 교육 기수·소속·가입 요청
- 학습 타이머·기록·통계
- 출결·재실·공간 점유
- 공간·팀·팀원 수명주기
- 커뮤니티·이미지 첨부
- Redis Presence·WebSocket
- 캐릭터·퀘스트·랭킹
- Telegram·RabbitMQ·Rule 기준값 연동

## 기술 구성

- Java 21, Spring Boot 4.1
- Spring Security, OAuth2 Resource Server
- Spring Data JPA, PostgreSQL 18.1, Flyway
- Redis, RabbitMQ, STOMP WebSocket
- Eureka Client, Testcontainers

## 빠른 검증

- 선행 조건: Docker 호환 Container Runtime 실행
- 테스트 DB: Testcontainers에서 임시 생성
- 로컬 PostgreSQL·Redis: 불필요

```bash
./mvnw verify
```

## 로컬 실행

```bash
cp .env.local.example .env.local
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

- Profile: `local`
- 설정 파일: 저장소 루트 `.env.local`
- 기본 Port: `8084`
- 기본 DB: `jdbc:postgresql://localhost:5432/learning_service`
- Redis 논리 DB: `REDIS_DATABASE`, 로컬 기본값 `0`
- Eureka: 기본 비활성화
- Health: <http://localhost:8084/actuator/health>

### Testcontainers 기반 E2E 연동 실행

실제 Learning Service를 임시 PostgreSQL과 함께 띄워 Frontend BFF·Gateway 연동을 확인할 때는
`E2eLearningServiceApplication`을 사용한다. 이 실행기는 PostgreSQL만 자동으로 준비하며,
Redis·RabbitMQ·Identity·Gateway·View는 검증 범위에 따라 별도로 필요하다.

```bash
./mvnw spring-boot:test-run \
  -Dspring-boot.run.main-class=site.omagotchi.learningservice.E2eLearningServiceApplication \
  -Dspring-boot.run.profiles=local
```

정의, IntelliJ 설정, 전체 서비스 실행 순서와 오류별 해결 방법은
[Learning Service E2E 실행·검증 가이드](docs/testing/Learning-Service-E2E-Guide.md)를 따른다.

### JWT Public Key

- 용도: Identity Access JWT 검증
- 기본 경로: `../identity-service/secrets/jwt-public.pem`
- Private Key의 Learning 복사 금지
- 경로 변경: `.env.local`의 `JWT_PUBLIC_KEY_LOCATION`

### 외부 자원

- PostgreSQL: 영속 Domain 데이터
- Redis: WebSocket Presence·Session TTL
- RabbitMQ: Rule 품질 데이터 소비·복구 Queue
- 첨부파일: `COMMUNITY_ATTACHMENT_STORAGE_ROOT`
- Telegram: 사용자 연동·Webhook

### Identity Service 연동

- 용도: 팀원 계정 상태·표시 이름 조회
- 호출 방식: Gateway를 경유하지 않는 직접 HTTP 호출
- 인증: Learning–Identity 관계 전용 HTTP Basic Credential
- 주소 선택
  - `local`·`dev`: 환경 파일의 고정 Identity 주소
  - `prod`: Eureka의 `identity-service`와 Client-side Load Balancing
- 장애 변환
  - 연결 실패·Timeout·Discovery 부재·`5xx`: `503 Service Unavailable`
  - 미등록 오류 Code·응답 계약 위반: `502 Bad Gateway`

## 환경 Profile

- `local`: `.env.local`, 로컬 PostgreSQL·Redis·RabbitMQ
- `dev`: `.env.dev`, 공유 개발 자원, Flyway 기본 비활성화
- `test`: Testcontainers DB·테스트 Key, 외부 자원 미사용
- `prod`: 운영 환경변수·Mount된 JWT Public Key, Eureka 활성화

## HTTP 경계

- 기본 Prefix: `/api/v1`
- 일반 보호 API: Access JWT 필수
- 관리자 API: `SYSTEM_ADMIN` 또는 기수 관리자 정책
- 공개 조회: `GET /api/v1/spaces`
- Telegram Webhook: `POST /api/v1/webhooks/telegram`, Access JWT 예외
- WebSocket Handshake: `/ws/**`
- 내부 사용자 식별자: Access JWT의 `sub` UUID

주요 Resource:

- `/api/v1/cohorts/**`: 기수·소속·출결 정책
- `/api/v1/cohorts/{cohortId}/attendance-records/**`: 출결
- `/api/v1/cohorts/{cohortId}/timer/**`: 학습 타이머
- `/api/v1/cohorts/{cohortId}/study-statistics/**`: 학습 통계
- `/api/v1/spaces/**`: 공간·점유
- `/api/v1/teams/**`: 팀·팀원
- `/api/v1/community/posts/**`: 커뮤니티
- `/api/v1/gamification/**`: 캐릭터·퀘스트
- `/api/v1/telegram/**`: 사용자 Telegram 연동
- `/api/v1/threshold-rules/**`: 센서 임계치 기준

- 최신 세부 계약: Spring REST Docs 기반 산출물로 관리 예정
- Frontend 연동 구현 요청서: [`docs/api/Frontend-Learning-Integration-Task-Brief.md`](docs/api/Front-LearningService/Frontend-Learning-Integration-Task-Brief.md)
- Frontend 상세 API 계약: [`docs/api/Frontend-Learning-API-Integration-Handoff.md`](docs/api/Front-LearningService/Frontend-Learning-API-Integration-Handoff.md)
- 위 인수인계 문서를 제외한 기존 `docs/api/` 문서는 과거 작업 참고 자료이며 최신 계약 근거로 사용하지 않음

## Database·Migration

- Schema: `learning_service`
- Migration: `src/main/resources/db/migration/`
- JPA 정책: `ddl-auto=validate`
- JDBC 시간대: `UTC`
- Identity 사용자 참조: JWT `sub`와 동일한 UUID `userId`
- 서비스 간 참조: Foreign Key 대신 논리 식별자 사용
- V1–V8 통합 기준선 적용 대상: 기존 Learning Flyway 이력이 없는 빈 Schema
- 기존 V1–V23 적용 개발 DB: 초기화 후 통합 기준선 재적용
- 적용 완료 Migration의 변경 금지
- Schema 변경의 신규 Version Migration 추가

### V1–V8 통합 기준선 전환

- 운영 최초 적용: Learning Flyway 이력이 없는 빈 Schema에 V1–V8 적용
- 기존 개발 DB 적용: 저장 데이터 백업과 공유 DB 사용자 승인 후 진행
- 초기화 범위: `learning_service` Schema 전용
- 사용 금지: 현재 Schema 상태를 보존하는 절차가 아닌 `baseline`·`repair`
- 적용 순서:

  1. Learning Service 중지
  2. DB 백업 및 초기화 승인 확인
  3. 기존 Schema 삭제 및 빈 Schema 재생성

     ```sql
     DROP SCHEMA learning_service CASCADE;
     CREATE SCHEMA learning_service;
     ```

  4. `FLYWAY_ENABLED=true` 설정으로 Learning Service 1회 기동
  5. `learning_service.flyway_schema_history`의 V1–V8 성공 상태 확인
  6. 공유 개발 환경의 `FLYWAY_ENABLED=false` 기본값 복원

## 코드 구조

- 교육: `cohort`, `attendance`, `study`, `statistics`
- 공간: `space`, `occupancy`, `team`
- 사용자 기능: `community`, `gamification`, `ranking`, `user`
- 연동: `realtime`, `telegram`, `rule`
- 공통: `global.security`, `global.exception`, `global.config`, `global.logging`
- 팀 영속성: `team.infrastructure.persistence`
- Identity 연동: `team.infrastructure.identity`
- 내부 계층: `domain` → `application` → `infrastructure`·`presentation`

## 운영 원칙

- 실제 Credential·Token·JWT Key의 기록 금지
- Frontend Session과 Learning Presence의 Redis 논리 DB 분리
- Telegram Webhook의 Access JWT 예외와 제공자 검증의 분리
- 공유 DB Migration의 담당 절차 외 실행 금지
- 운영 첨부파일 Volume 연결

## 관련 문서

- [Backend Code Structure](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/10-backend-code-structure.md)
- [공통 예외 처리](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/04-error-handling.md)
- [REST API Convention](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/09-rest-api-convention.md)
- [HTTP Request ID](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/08-http-request-id.md)
