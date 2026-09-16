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
./mvnw clean verify
```

배포용 Docker 빌드는 테스트와 문서 생성을 생략하고 애플리케이션 JAR만 패키징합니다.
REST Docs 생성과 누락 검증은 PR CI의 `verify`에서 수행합니다.

`clean verify`는 테스트를 생략하지 않고 Controller 계약 테스트로
`target/generated-snippets/`를 만든 뒤, Spring REST Docs 조각을 조합해
`target/generated-docs/index.html`을 생성한다.

`clean`이 `target/` 전체를 삭제하므로, 문서 조각과 HTML은 같은 명령의
테스트·문서화 단계가 다시 실행된 뒤에 생긴다. IntelliJ IDEA에서 확인할 때도 소스 문서
`src/docs/asciidoc/index.adoc`가 참조하는 조각은 `target/generated-snippets/`,
최종 HTML은 `target/generated-docs/index.html`에서 찾는다. IntelliJ IDEA에서
개별 요청을 확인하려면 테스트 실행 후 `target/generated-snippets/<domain>/<id>/`
아래의 `http-request.adoc`, `http-response.adoc`, `request-fields.adoc`,
`response-fields.adoc`, `path-parameters.adoc`, `query-parameters.adoc`를 연다.
REST Docs의 `document()` 식별자와 Asciidoctor의 `snippets` attribute가 같은 경로를
사용한다. 테스트가 request/response snippet을 자동 생성하고, 개발자가 각 테스트의
`document()` 식별자·필드 설명과 `src/docs/asciidoc/index.adoc`의 domain include를 관리한다.

Controller HTTP 계약과 REST Docs는 `@WebMvcTest`와 `@AutoConfigureRestDocs`로 검증한다.
대상 Controller와 실제 Security/JWT 설정을 로드하고 서비스·오류 로깅 경계는
`@MockitoBean`으로 격리한다. 주입받은 `MockMvc`를 그대로 사용하며,
인증이 필요한 요청에는 `TestJwtKeyConfig`로 발급한 JWT를 Authorization 헤더에 명시한다.
JWT의 주체·역할은 해당 테스트의 서비스 입력 검증과 일치시킨다.
내부 Basic 인증과 simulator는 각 경로의 실제 설정·프로필을 사용한다.

검증 순서는 HTTP 상태·응답 assertion 이후 `document()`이며, 기존 문서 ID를 유지한다.
새 API에는 정상 응답뿐 아니라 의미 있는 validation·인증 실패 조건도 검증한다.
MVC slice와 별도로 서비스 단위 테스트, 하류 HTTP 계약 테스트, 전체 통합 테스트를 유지한다.

새 테스트의 어노테이션·줄바꿈·메서드 DisplayName·Given/When/Then 형식은
[컨트롤러 테스트 작성 기준](docs/testing/controller-test-style.md)을 따른다.

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

- 타이머·첨부파일 한도·Presence TTL·Telegram 연동 TTL·호출 timeout: `application.yaml` 기본값 사용
  - 기존 환경 파일의 같은 키가 있으면 해당 값 우선
  - DB·Broker·API 주소·Credential·첨부파일 Bucket의 필수 주입 유지

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

### 런타임 기초·기동 실패 진단

DB 커넥션 풀(HikariCP), 실행기 3종의 차이, `.env.local` 키 누락 진단은
[Learning Service 런타임 기초 가이드](docs/runtime/Learning-Service-Runtime-Guide.md)를 참고한다.

`git pull` 후 `Could not resolve placeholder`로 기동이 실패하면 다음을 먼저 실행한다.

```bash
grep -oE '\$\{[A-Z0-9_]+\}' src/main/resources/application.yaml \
  | tr -d '${}' | sort -u > /tmp/req.txt
grep -oE '^[A-Z0-9_]+' .env.local | sort -u > /tmp/have.txt
echo "누락된 키:"; comm -23 /tmp/req.txt /tmp/have.txt
```

### JWT Public Key

- 용도: Identity Access JWT 검증
- 기본 경로: `../identity-service/secrets/jwt-public.pem`
- Private Key의 Learning 복사 금지
- 경로 변경: `.env.local`의 `JWT_PUBLIC_KEY_LOCATION`

### 외부 자원

- PostgreSQL: 영속 Domain 데이터
- Redis: WebSocket Presence·Session TTL
- RabbitMQ: Rule 품질 데이터 소비·복구 Queue
- MinIO: 커뮤니티 첨부파일 객체 저장소 (`MINIO_ENDPOINT`, `COMMUNITY_ATTACHMENT_BUCKET`)
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

### Rule Service 연동

- 용도: Rule Engine의 초기 적재·5분 주기 누락 보정용 임계치 기준 조회
- 경로: `GET /api/v1/internal/threshold-rules`
- 호출 방식: Gateway를 경유하지 않는 Rule→Learning 직접 HTTP 호출
- 인증: Rule–Learning 관계 전용 HTTP Basic Credential
- 공개 사용자 API: `/api/v1/threshold-rules/**`의 Access JWT 정책과 분리

## 환경 Profile

- `local`: `.env.local`, 로컬 PostgreSQL·Redis·RabbitMQ
- `dev`: `.env.dev`, 공유 개발 자원, Flyway 기본 비활성화
- `test`: Testcontainers DB·테스트 Key, 외부 자원 미사용
- `prod`: 운영 환경변수·Mount된 JWT Public Key, Eureka 활성화

## 관측 운영 설정

- 운영 메트릭: `/actuator/prometheus`의 인증 예외, Host Port 미노출·내부 Prometheus 조회
  - 외부 Nginx의 관리 경로 차단, 같은 Host의 관리자·Docker 제어 권한 보유자에 대한 격리 보장 없음
- 관측 식별: 운영 Compose의 `SERVICE_VERSION`·`SERVICE_NODE_NAME`·`SERVICE_ENVIRONMENT` 주입
- Trace 전송: `TRACING_EXPORT_ENABLED` 기본 비활성, 운영 Compose의 실제 Collector 주소 주입
  - Compose 밖에서 운영 실행 시 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`에 실제 Collector 주소 지정 필수
- 공통 수집·접근 경계·운영 확인: [Infra 메트릭·Trace 가이드](https://github.com/nhnacademy-aiot3-omagotchi/omagotchi-infra/blob/main/observability/metrics-tracing.md#3-서비스-연결)

## HTTP 경계

- 기본 Prefix: `/api/v1`
- 일반 보호 API: Access JWT 필수
- Rule 내부 조회: `GET /api/v1/internal/threshold-rules`, Rule 전용 HTTP Basic Credential
- 관리자 API: `SYSTEM_ADMIN` 또는 기수 관리자 정책
- 공개 조회: `GET /api/v1/spaces`
- Telegram Webhook: `POST /api/v1/webhooks/telegram`, Access JWT 예외
- WebSocket Handshake: `/ws/**`
- 내부 사용자 식별자: Access JWT의 `sub` UUID

주요 Resource:

- `/api/v1/cohorts/**`: 기수·소속·출결 정책
- `POST /api/v1/cohorts/managers/search`: 사용자 묶음의 기수 운영 권한(MANAGER) 일괄 조회, `SYSTEM_ADMIN` 전용, 요청당 최대 100명
- `/api/v1/cohorts/{cohort-id}/attendance-records/**`: 출결
- `/api/v1/cohorts/{cohort-id}/timer/**`: 학습 타이머
- `/api/v1/cohorts/{cohort-id}/study-statistics/**`: 학습 통계
- `/api/v1/spaces/**`: 공간·점유
- `/api/v1/teams/**`: 팀·팀원
- `/api/v1/community/posts/**`: 커뮤니티
- `/api/v1/gamification/**`: 캐릭터·퀘스트
- `/api/v1/telegram/**`: 사용자 Telegram 연동
- `/api/v1/threshold-rules/**`: 센서 임계치 기준

- 최신 세부 계약: [REST Docs source index](src/docs/asciidoc/index.adoc)와 테스트가 생성한
  [HTML 산출물](target/generated-docs/index.html)
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
- 공유 MinIO의 버킷 이름 선점과 서비스 전용 Credential 사용

## 관련 문서

- [Backend Code Structure](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/10-backend-code-structure.md)
- [공통 예외 처리](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/04-error-handling.md)
- [REST API Convention](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/09-rest-api-convention.md)
- [HTTP Request ID](https://github.com/nhnacademy-aiot3-omagotchi/docs/blob/main/50-guides/08-http-request-id.md)
