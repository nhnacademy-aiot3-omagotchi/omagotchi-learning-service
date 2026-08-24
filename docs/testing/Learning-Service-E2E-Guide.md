# Learning Service E2E 실행·검증 가이드

- 상태: 현재 실행 구조 설명 및 팀 공통 가이드
- 대상: Learning Service 개발자, Frontend BFF 연동 담당자
- 관련 실행 클래스:
  - `src/test/java/site/omagotchi/learningservice/E2eLearningServiceApplication.java`
  - `src/test/java/site/omagotchi/learningservice/E2eTestcontainersConfiguration.java`

## 1. E2E가 무엇인가

E2E(End-to-End) 테스트는 사용자의 한 동작이 시작되는 지점부터 최종 데이터 저장과 응답까지
실제 경계를 통과하는지 확인하는 검증이다.

Omagotchi의 전체 연동 흐름은 다음과 같다.

```text
Browser
  → View BFF (/bff/v1/**)
  → Gateway
  → Learning Service (/api/v1/**)
  → PostgreSQL·Redis·RabbitMQ
  → 응답이 같은 경로로 Browser까지 반환
```

예를 들어 사용자가 출석 버튼을 누르는 E2E 검증은 버튼이 보이는지만 확인하지 않는다.
Browser Session, BFF의 Bearer Token 변환, Gateway Routing, Learning의 JWT·기수 권한 검사,
출석 저장, 화면 응답까지 한 흐름으로 확인한다.

## 2. 테스트 종류와 차이

| 구분 | 확인 범위 | 빠르기 | 대표 사용 시점 |
|---|---|---:|---|
| 단위 테스트 | 메서드·클래스 하나 | 가장 빠름 | 계산, 정책, 예외 분기 개발 중 |
| 계층·통합 테스트 | Controller, Repository, DB 등 일부 경계 | 빠름 | HTTP 계약, Query, Flyway 검증 |
| `./mvnw verify` | 저장소의 자동 테스트 전체 | 보통 | 커밋·PR 전 회귀 검사 |
| Learning E2E 실행기 | 실제 Learning 앱 + 임시 PostgreSQL | 보통 | 실제 API를 띄워 다른 서비스와 연동 |
| 전체 시스템 E2E | Browser부터 Learning 저장소까지 | 가장 느림 | 주요 기능 완료, 병합·배포 전 |

작은 정책 하나를 수정할 때마다 전체 시스템을 띄울 필요는 없다. 단위·통합 테스트로 빠르게
검증한 뒤, 서비스 경계를 바꾼 작업에 E2E 검증을 추가하는 방식이 적합하다.

## 3. 현재 `E2eLearningServiceApplication`의 정확한 역할

이 클래스의 이름에 `E2E`가 들어 있지만, **이 클래스 하나가 전체 시스템 E2E 테스트를
자동 수행하는 것은 아니다.** 실제 Learning Service를 Testcontainers PostgreSQL과 함께
실행해 주는 로컬 연동 실행기다.

실행 과정은 다음과 같다.

```text
E2eLearningServiceApplication
  → 실제 LearningServiceApplication 실행
  → E2eTestcontainersConfiguration 추가
  → PostgreSQL 18.1 Container 시작
  → @ServiceConnection이 임시 DB의 주소·계정을 DataSource에 연결
  → Flyway Migration 실행
  → JPA Schema 검증
  → 실제 Learning HTTP API 기동
```

따라서 개발자가 PostgreSQL을 직접 설치하거나 `localhost:5432`에 별도 DB를 만들지 않아도
항상 깨끗한 DB에서 API를 확인할 수 있다. 애플리케이션을 종료하면 Container와 그 데이터도
폐기된다.

### 이 실행기가 자동으로 띄우지 않는 것

현재 설정은 PostgreSQL Container만 제공한다. 아래 구성요소는 필요한 검증 범위에 따라 별도로
실행하거나 올바른 개발 환경 주소를 설정해야 한다.

- Redis
- RabbitMQ
- Identity Service
- Gateway
- View BFF와 Browser 화면

Redis는 실제 기능이 호출될 때 연결될 수 있고, RabbitMQ Listener는 Broker 연결 실패 후 재시도할
수 있으므로 두 서비스가 실행되지 않았다고 ApplicationContext가 반드시 즉시 실패하는 것은 아니다.
다만 Redis·RabbitMQ를 사용하는 기능은 해당 서비스가 연결된 환경에서만 정상 동작을 검증할 수
있다. Identity Service, Gateway, View BFF와 Browser는 로그인부터 화면까지 전체 요청 흐름을
검증할 때 함께 실행한다.

## 4. 지금 프로젝트에 적합한 이유

현재는 View BFF, Gateway, Learning API 계약을 실제로 연결하는 단계이므로 다음 문제를 단위
테스트만으로는 충분히 발견하기 어렵다.

- BFF 경로와 Learning API 경로·Method가 서로 다른 문제
- Session의 Access Token이 Bearer Header로 전달되지 않는 문제
- Gateway Routing 또는 JWT Public Key 설정 문제
- DTO 필드명, 날짜 기준, Pagination 계약 불일치
- Flyway Migration과 JPA Entity가 맞지 않는 문제
- 빈 DB에서는 실패하지만 개발자의 기존 DB에서는 우연히 통과하는 문제
- Redis·RabbitMQ 등 외부 연결 설정 누락

Testcontainers로 매번 같은 PostgreSQL 기준선을 만들면 개인 DB 상태에 영향을 받지 않고 팀원이
같은 조건을 재현할 수 있다. 그 위에 실제 Learning 애플리케이션을 띄우므로 Frontend BFF와의
연결도 Mock이 아닌 실제 HTTP 경계에서 확인할 수 있다.

## 5. 언제 사용해야 하는가

다음 변경에는 E2E 실행 또는 전체 연동 검증을 권장한다.

- Controller endpoint, Request·Response DTO, 오류 응답 계약 변경
- 인증·인가, JWT, 기수 소속 판정 변경
- Flyway Migration, Entity, QueryDSL Query 변경
- View BFF 또는 Gateway Routing 연결
- 출결, 학습 기록, 퀘스트처럼 여러 Domain이 연동되는 흐름
- Redis Presence, RabbitMQ Event 처리 변경
- 병합 전 주요 사용자 흐름의 최종 확인

다음 작업은 보통 단위·Storybook·일반 빌드 검증으로 충분하다.

- 순수 계산 메서드의 작은 변경
- Backend와 무관한 CSS·Storybook 시각 변경
- 문서만 수정한 작업
- 이미 충분한 자동 통합 테스트가 있는 단순 내부 리팩터링

## 6. 실행 전 준비

1. Docker Desktop 또는 OrbStack 같은 Docker 호환 Runtime을 실행한다.
2. 저장소 루트에서 `.env.local`을 준비한다.

   ```bash
   cp .env.local.example .env.local
   ```

3. `.env.local`에 필요한 공개 설정을 입력한다.
   - `SERVER_PORT`
   - Redis 연결 정보
   - RabbitMQ 연결 정보
   - `JWT_PUBLIC_KEY_LOCATION`
4. JWT는 Identity의 **Public Key만** 연결한다. Private Key를 복사하지 않는다.
5. `.env.local`과 실제 Credential은 Git에 올리지 않는다.

`.env.local`은 `application-local.yaml`에서 상대 경로로 읽는다. 따라서 Working directory가
Learning Service 저장소 루트가 아니면 설정을 찾지 못할 수 있다.

## 7. IntelliJ 실행 설정

Run/Debug Configuration에서 다음 값을 사용한다.

| 항목 | 값 |
|---|---|
| Main class | `site.omagotchi.learningservice.E2eLearningServiceApplication` |
| Module | `learning-service` |
| Active profiles | `local` |
| Working directory | Learning Service 저장소 루트 |
| Java | 21 |

Active profiles 칸에는 `local`만 입력한다. 아래 세 방식 중 하나만 사용해야 한다.

```text
Active profiles: local
```

```text
VM options: -Dspring.profiles.active=local
```

```text
Program arguments: --spring.profiles.active=local
```

Active profiles 칸에 `--spring.profiles.active=local`을 입력하면 IntelliJ가 앞에
`-Dspring.profiles.active=`를 다시 붙여 잘못된 프로필 이름이 된다.

## 8. CLI 실행

저장소 루트에서 Test Runtime classpath를 사용하는 Spring Boot 실행 목표를 호출한다.

```bash
./mvnw spring-boot:test-run \
  -Dspring-boot.run.main-class=site.omagotchi.learningservice.E2eLearningServiceApplication \
  -Dspring-boot.run.profiles=local
```

일반 `spring-boot:run`은 기본적으로 `src/main` classpath를 사용한다. `src/test`에 있는 E2E
실행기와 Testcontainers 설정을 사용하려면 `spring-boot:test-run`이 맞다.

자동 테스트 전체만 실행하려면 다음 명령을 사용한다.

```bash
./mvnw verify
```

`verify`는 테스트 후 종료하는 검증이고, `test-run`은 다른 서비스와 연동할 수 있도록 실제
애플리케이션을 계속 실행한다.

## 9. 정상 기동 확인

다음을 모두 확인한다.

- `local` Profile이 활성화되었다는 로그
- PostgreSQL Container 시작 로그
- Flyway Migration 성공
- JPA `ddl-auto=validate` 통과
- `Started E2eLearningServiceApplication` 로그
- 설정한 Port의 Health 응답

```text
GET http://localhost:<SERVER_PORT>/actuator/health
```

예를 들어 `.env.local`에 `SERVER_PORT=8084`를 설정했다면
`http://localhost:8084/actuator/health`로 확인한다.

`Application run failed`가 출력되었다면 Process 종료 코드가 `0`이어도 기동 성공으로 보지 않는다.

## 10. 전체 Frontend 연동 검증

Browser부터 확인할 때의 기본 실행 순서는 다음과 같다.

1. Redis
2. Identity Service
3. Learning Service E2E 실행기
4. Gateway
5. View BFF
6. Browser에서 로그인 후 기능 검증

RabbitMQ Event를 확인하는 기능이면 RabbitMQ 연결도 먼저 준비한다. 원격 개발 Broker를 쓰는
환경에서는 `.env.local`의 Host가 `localhost` 기본값으로 남아 있지 않은지 확인한다.

최소 검증 항목:

- 로그인 Session이 유지되는가
- Browser가 `/bff/v1/**`만 호출하는가
- BFF가 JWT를 Learning 요청에 전달하는가
- Profile·현재 캐릭터가 실제 응답과 같은가
- 승인 기수가 없을 때 정의된 업무 오류가 보이는가
- 출결·학습 기록 저장 후 재조회 결과가 같은가
- 허용된 4xx는 화면에 전달되고 하류 5xx 상세는 숨겨지는가
- 응답의 `requestId`로 Frontend와 Learning 로그를 연결할 수 있는가

## 11. 자주 만나는 오류

| 증상 | 의미 | 확인 방법 |
|---|---|---|
| `Connection to localhost:5432 refused` | 일반 Main class를 실행했거나 Testcontainers 설정이 붙지 않음 | E2E Main class와 test classpath 확인 |
| `SERVER_PORT` 누락 | `local` Profile 또는 `.env.local`을 읽지 못함 | Profile, Working directory, 파일 위치 확인 |
| `Profile '--spring.profiles.active=local'...` | Active profiles 칸에 전체 옵션을 잘못 입력 | `local`만 입력 |
| JWT Public Key를 읽지 못함 | 경로가 틀렸거나 파일이 없음 | 저장소 루트 기준 경로와 읽기 권한 확인 |
| `localhost:5672 Connection refused` | RabbitMQ Host가 기본값으로 남았거나 Broker가 꺼짐 | `.env.local`의 실제 Host·Port 확인 |
| `Attempt to recreate a file for type Q...` | QueryDSL Q Class를 IDE와 Maven이 중복 생성 | Maven Reload, Annotation Processor 중복 제거, `clean compile` |
| Health는 성공하지만 화면 API가 401 | Identity 로그인 또는 BFF Token relay 문제 | Session, Redis, Bearer Header 순서로 확인 |
| 502·503 | Gateway/Learning 계약 불일치 또는 하류 서비스 미기동 | `requestId`와 각 서비스 로그 확인 |

QueryDSL 오류가 반복되면 생성 위치가 `target/generated-sources/annotations` 한 곳인지 확인하고,
IntelliJ의 Annotation Processor가 같은 Q Class를 별도 경로에 다시 만들지 않도록 한다.

## 12. 안전 규칙

- `.env.local`, JWT Private Key, 실제 Credential을 커밋하지 않는다.
- E2E용 PostgreSQL 데이터는 실행 종료 시 폐기된다고 가정한다.
- 보존이 필요한 수동 검증 데이터는 공유 개발 DB 절차를 별도로 따른다.
- Testcontainers가 띄운 DB와 팀 공유 DB를 혼동하지 않는다.
- E2E 성공만으로 단위 테스트를 대체하지 않는다.
- 외부 서비스가 없어서 건너뛴 검증 항목은 PR에 명시한다.

## 13. 한 문장 정리

> Learning E2E 실행기는 실제 Learning Service를 깨끗한 임시 PostgreSQL과 함께 띄우는 도구이며,
> Identity·Gateway·View까지 함께 실행했을 때 Browser부터 저장소까지의 전체 E2E 흐름을 검증할
> 수 있다.
