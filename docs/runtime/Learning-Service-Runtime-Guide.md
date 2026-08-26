# Learning Service 런타임 기초 가이드

> 상태: 현재 구현 설명 · 최종 갱신 2026-08-25
> 대상: Learning Service를 로컬에서 띄우거나, 기동 실패·DB 지연을 겪는 개발자
> 범위: DB 커넥션 풀(HikariCP), 실행기 3종의 차이, 환경 변수 누락 진단
> 비범위: E2E 정의와 전체 서비스 실행 순서 →
> [Learning Service E2E 실행·검증 가이드](../testing/Learning-Service-E2E-Guide.md)

---

## 1. 이 문서를 봐야 할 때

| 증상 | 절 |
| --- | --- |
| 로그에 `HikariPool-1` 이 보이는데 이게 뭔지 모르겠다 | §2 |
| DB가 느린 것 같다 / 요청이 30초 뒤 실패한다 | §2.5 |
| `Could not resolve placeholder '...'` 로 기동이 안 된다 | §4 |
| `Connection to localhost:5432 refused` | §3, §5 |
| 어떤 Main class로 띄워야 하는지 모르겠다 | §3 |
| 토큰은 정상인데 전부 `401` 이다 | §3.2 |

---

## 2. HikariCP — DB 커넥션 풀

### 2.1 무엇인가

DB 연결은 만드는 데 비싼 자원이다. TCP 연결·인증·세션 초기화까지 한 번에 수십 ms가 든다.

```text
풀 없이:  요청 → 연결 생성(약 50ms) → 쿼리(2ms) → 연결 종료 → 응답
풀 있이:  요청 → 풀에서 빌림(0.1ms) → 쿼리(2ms) → 풀에 반납 → 응답
```

미리 연결 몇 개를 만들어 두고 **빌려주고 돌려받는** 것이 커넥션 풀이다.
HikariCP는 그중 가장 빠르며 **Spring Boot 2.0부터 기본 구현체**다.

### 2.2 어디서 왔나 — 선언한 적이 없다

```text
pom.xml
  └─ spring-boot-starter-data-jpa
       └─ spring-boot-starter-jdbc
            └─ HikariCP        ← 직접 선언하지 않았지만 여기 있다
```

`application.yaml`의 `spring.datasource.url/username/password`만 있으면 Hikari가 그 정보로
풀을 만든다. **현재 `hikari:` 설정 블록은 하나도 없고 전부 기본값으로 동작한다.**

### 2.3 로그 읽는 법

정상 기동:

```text
HikariPool-1 - Starting...
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
HikariPool-1 - Start completed.
```

DB가 없을 때:

```text
HikariPool-1 - Exception during pool initialization.
org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
```

**DB는 기동 시점에 hard fail 한다.** Hikari가 풀을 즉시 초기화하고 Flyway가 마이그레이션을
실행하기 때문이다. 반면 Redis와 RabbitMQ는 연결이 지연 생성이라 기동 자체는 통과하는 경우가
많고, **해당 기능을 호출하는 시점에** 실패한다. Presence가 조용히 안 되는데 앱은 떠 있다면
Redis를 의심한다.

### 2.4 현재 적용 중인 기본값

| 항목 | 기본값 | 의미 |
| --- | --- | --- |
| `maximum-pool-size` | **10** | 동시에 열어 둘 최대 연결 수 |
| `minimum-idle` | `maximum-pool-size`와 동일 (10) | 놀고 있어도 유지할 최소 개수 |
| `connection-timeout` | **30초** | 풀이 꽉 찼을 때 대기 시간. 초과하면 예외 |
| `idle-timeout` | 10분 | 유휴 연결 정리 |
| `max-lifetime` | 30분 | 연결 하나의 최대 수명 |
| `leak-detection-threshold` | **0 (비활성)** | 반납 누락 탐지 |

**함정:** `minimum-idle`의 기본값이 `maximum-pool-size`와 같으므로 **`idle-timeout`은 사실상
동작하지 않는다.** 줄일 유휴 연결이 없기 때문이다. 유휴 연결을 실제로 줄이려면
`minimum-idle`을 명시적으로 낮춰야 한다. 지금 규모에서는 그럴 이유가 없다.

### 2.5 이 저장소의 실제 위험 지점 2가지

풀 고갈은 **"DB가 느리다"** 처럼 보이지만 DB는 멀쩡한 경우가 대부분이다.
요청이 30초를 기다린 뒤 실패한다면 `connection-timeout` 기본값에 걸린 것이고, 원인은
**커넥션을 오래 붙잡는 코드**다.

#### ① 비관적 락 + 풀 크기 10

`@Lock(LockModeType.PESSIMISTIC_WRITE)`를 다음 5곳에서 사용한다.

```text
attendance/infrastructure/AttendanceRecordRepository
gamification/infrastructure/UserDailyQuestRepository
gamification/infrastructure/UserCharacterRepository
occupancy/infrastructure/VacancyAlertJpaRepository
occupancy/infrastructure/RoomOccupancyJpaRepository
```

비관적 락은 **트랜잭션이 끝날 때까지 커넥션을 붙잡는다.** 락 대기가 길어지면 연결 10개가
전부 대기 상태가 되고, 11번째 요청부터 30초를 기다리다 실패한다.

#### ② `@Transactional` 안에서 외부 HTTP 호출

이쪽이 더 위험하다. 트랜잭션 안에서 Identity·Rule·Telegram·InfluxDB를 호출하면
**HTTP 응답을 기다리는 내내 DB 커넥션을 쥐고 있다.** 상대 서비스가 느려지면 이쪽 풀이 마른다.

원칙: **외부 호출은 트랜잭션 밖으로 뺀다.** 트랜잭션 안에서 꼭 호출해야 한다면 그 호출에
짧은 timeout이 걸려 있는지 확인한다.

### 2.6 넣을 값어치가 있는 설정 하나

성능 튜닝은 지금 필요 없다. 다만 **누수 탐지는 켜 둘 값어치가 있다.**

```yaml
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      # 커넥션을 이 시간(ms) 이상 반납하지 않으면 stack trace와 함께 경고를 남긴다.
      # 트랜잭션 안에서 외부 HTTP를 호출하거나 락을 오래 잡는 코드를 찾아낸다.
      # 예외를 던지지 않으므로 운영 동작에 영향이 없다. 0이면 비활성이며 최소값은 2000이다.
      leak-detection-threshold: 20000
```

문제가 있으면 **어느 줄에서 붙잡고 있는지** 정확히 찍힌다.

```text
Connection leak detection triggered for org.postgresql.jdbc.PgConnection@...,
stack trace follows
  at site.omagotchi.learningservice.xxx.XxxService.doSomething(XxxService.java:42)
```

문제가 없으면 아무것도 찍히지 않으므로 켜 두는 비용이 없다.

`connection-timeout`을 5초로 줄여 장애를 빨리 드러내는 방법도 있지만, 순간 부하에서도 실패가
나므로 **처음에는 기본값(30초)을 두고 누수 탐지만 켜는 것을 권한다.**

### 2.7 언제 풀 크기를 건드리나

**지금은 건드리지 않는다.** 30명 규모에서 연결 10개는 충분하다.
다음 신호가 실제로 관측될 때만 조정한다.

| 신호 | 확인 방법 | 조치 |
| --- | --- | --- |
| `connection is not available, request timed out after 30000ms` | 애플리케이션 로그 | 먼저 §2.5의 원인부터 찾는다. 풀 크기를 올리는 것은 마지막 수단이다 |
| 유휴 연결이 DB의 `max_connections`를 압박 | `SELECT count(*) FROM pg_stat_activity` | `minimum-idle`을 낮춘다 |
| 인스턴스를 여러 대로 늘림 | — | `인스턴스 수 × maximum-pool-size ≤ DB max_connections` 를 계산한다 |

**풀 크기를 키우면 문제가 사라지는 것처럼 보이지만 대개 원인을 뒤로 미룰 뿐이다.**
연결을 오래 붙잡는 코드를 먼저 찾는다.

---

## 3. 실행기 3종 — 언제 무엇을 쓰나

`src/test`에 Main class가 두 개 더 있어 헷갈리기 쉽다. **이름은 비슷하지만 용도가 다르다.**

| 실행기 | PostgreSQL | JWT 키 | Identity 호출 | 용도 |
| --- | --- | --- | --- | --- |
| `LearningServiceApplication` (`src/main`) | **로컬에 직접 설치·기동** | 실제 PEM | 실제 Identity | 운영과 같은 기동 경로 검증 |
| `TestLearningServiceApplication` | Testcontainers 자동 | **테스트용 임시 키** | **스텁** | Learning 단독 격리 실행 |
| `E2eLearningServiceApplication` | Testcontainers 자동 | 실제 PEM | 실제 Identity | **4개 서비스 통합 검증** |

### 3.1 두 테스트 설정의 실제 차이

| | `TestcontainersConfiguration` | `E2eTestcontainersConfiguration` |
| --- | --- | --- |
| PostgreSQL 컨테이너 | O | O |
| `TestJwtKeyConfig` import | **O** | X |
| `IdentityAccountClient` 스텁 (`@Primary`) | **O** | X |

### 3.2 통합 검증에 `TestLearningServiceApplication`을 쓰면 안 되는 이유

`TestJwtKeyConfig`는 **실행할 때마다 새 RSA 키페어를 생성**해 `jwtPublicKey` 빈으로 등록한다.

```java
private static final KeyPair KEY_PAIR = generateKeyPair();   // 매 실행마다 새로 생성

@Bean
RSAPublicKey jwtPublicKey() {
    return (RSAPublicKey) KEY_PAIR.getPublic();
}
```

**실제 Identity가 서명한 토큰이 전부 `401`이 된다.** 서명한 키와 검증하는 키가 다르기 때문이다.
앱은 정상 기동하고 요청만 조용히 401이 되므로 원인을 찾기 어렵다.

`IdentityAccountClient` 스텁도 함께 들어간다.

```java
public IdentityAccountState getState(UUID userId) {
    return IdentityAccountState.ACTIVE;      // 무조건 ACTIVE
}
public Map<UUID, String> findDisplayNames(...) {
    return ... UUID::toString;               // 닉네임 대신 UUID 문자열
}
```

즉 **통합 검증이 통합을 검증하지 않게 된다.** Identity를 가짜로 바꿔 놓고 "Identity 연동이
잘 된다"고 판정하는 셈이다.

### 3.3 E2E 실행기에는 Redis가 없다

`E2eTestcontainersConfiguration`은 **PostgreSQL만** 준비한다. Presence는
`StringRedisTemplate`으로 Redis에 상태를 저장하므로, Presence를 검증하려면 Redis를 따로 띄운다.

```bash
docker run -d --name omagotchi-redis -p 6379:6379 redis:7.4-alpine
```

Presence 검증이 잦아지면 `E2eTestcontainersConfiguration`에 컨테이너를 추가하는 편이 낫다.

```java
@Bean
@ServiceConnection(name = "redis")
GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
}
```

### 3.4 실행 명령

```bash
# 통합 검증 (권장)
./mvnw spring-boot:test-run \
  -Dspring-boot.run.main-class=site.omagotchi.learningservice.E2eLearningServiceApplication \
  -Dspring-boot.run.profiles=local

# 운영과 같은 기동 경로 (로컬 PostgreSQL 필요)
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

Property 이름은 `spring-boot.run.main-class`(하이픈)다. `mainClass`(카멜)는 인식되지 않는다.
`pom.xml`에 넣을 때는 `<properties>`의 `<spring-boot.run.main-class>`에 넣는다.
Plugin `<configuration>`의 `<mainClass>`에 넣으면 운영 JAR의 Start-Class가 test scope
클래스로 바뀌어 배포가 깨진다.

---

## 4. 환경 변수 — `.env.local` 누락 진단

### 4.1 왜 조용히 실패하는가

`application-local.yaml`이 `.env.local`을 읽고, `application.yaml`은 **기본값 없는
플레이스홀더**를 사용한다.

```yaml
  http:
    clients:
      connect-timeout: ${IDENTITY_SERVICE_CONNECT_TIMEOUT}   # 기본값 없음
```

키가 없으면 `Could not resolve placeholder`로 **기동 자체가 실패한다.**
`${KEY:기본값}` 형태였다면 넘어갔겠지만, 누락을 즉시 드러내려는 의도적인 설계다.

`.env.local`은 `.gitignore` 대상이므로 **팀원이 `application.yaml`에 새 키를 추가해도
내 로컬 파일은 자동으로 따라오지 않는다.** `.env.local.example`만 갱신된다.

### 4.2 누락 키 점검 스크립트

저장소 루트에서 실행한다. **아무것도 출력되지 않으면 정상이다.**

```bash
grep -oE '\$\{[A-Z0-9_]+\}' src/main/resources/application.yaml \
  | tr -d '${}' | sort -u > /tmp/req.txt
grep -oE '^[A-Z0-9_]+' .env.local | sort -u > /tmp/have.txt
echo "누락된 키:"; comm -23 /tmp/req.txt /tmp/have.txt
```

`.env.local.example`에서 누락분만 뽑아 붙이려면 다음을 이어서 실행한다.

```bash
comm -23 /tmp/req.txt /tmp/have.txt | while read -r key; do
  grep "^${key}=" .env.local.example
done >> .env.local
```

**비밀번호 계열은 붙인 뒤 실제 값으로 바꾼다.** `.env.local.example`의 값은
`local-only-change-this-...` 형태의 자리표시자다.

### 4.3 사례 (2026-08-25)

`application.yaml`과 `.env.local.example`이 08-25 00:20에 갱신되었으나 로컬 `.env.local`은
08-22에 머물러 있어 다음 8개가 누락되어 기동이 실패했다.

```text
IDENTITY_SERVICE_BASE_URL
IDENTITY_SERVICE_CONNECT_TIMEOUT
IDENTITY_SERVICE_READ_TIMEOUT
LEARNING_IDENTITY_USERNAME
LEARNING_IDENTITY_PASSWORD
PREDICTION_SERVICE_BASE_URL
RULE_LEARNING_USERNAME
RULE_LEARNING_PASSWORD
```

**같은 일이 반복되므로 `git pull` 후 기동이 안 되면 §4.2를 먼저 돌린다.**

Credential 계열(`LEARNING_IDENTITY_*`, `RULE_LEARNING_*`)은 상대 서비스가 아는 값과
일치해야 한다. 기동은 되는데 호출이 `401`이면 담당자에게 값을 맞춰 달라고 요청한다.
**기동 실패와 호출 실패는 다른 문제이므로 순서대로 잡는다.**

---

## 5. 증상별 원인 찾기

| 증상 | 먼저 의심할 것 | 확인 위치 |
| --- | --- | --- |
| `Could not resolve placeholder '...'` | `.env.local` 키 누락 | §4.2 스크립트 |
| `Connection to localhost:5432 refused` | 로컬 PostgreSQL 없음 또는 일반 Main class 실행 | E2E 실행기 사용 (§3.4) |
| `HikariPool-1 - Exception during pool initialization` | 위와 같음 | 같음 |
| 토큰은 정상인데 모든 요청이 `401` | `TestLearningServiceApplication`으로 띄움 | §3.2 |
| 사용자가 전부 ACTIVE, 닉네임이 UUID | 같음 | §3.2 |
| 앱은 떴는데 Presence만 안 됨 | Redis 미기동 | §3.3 |
| 요청이 30초 뒤 실패 | 커넥션 풀 고갈 | §2.5 |
| "DB가 느리다" | 대개 DB가 아니라 커넥션 반납 지연 | §2.6 누수 탐지 |
| `JWT public key를 읽을 수 없습니다` | `JWT_PUBLIC_KEY_LOCATION` 상대 경로 문제 | 절대 경로로 지정 |

---

## 6. 참고

| 목적 | 위치 |
| --- | --- |
| E2E 정의·전체 서비스 실행 순서 | [../testing/Learning-Service-E2E-Guide.md](../testing/Learning-Service-E2E-Guide.md) |
| 로컬 실행 요약 | [../../README.md](../../README.md) |
| 커넥션 풀 설정 위치 | `src/main/resources/application.yaml`의 `spring.datasource` |
| 환경 변수 로딩 | `src/main/resources/application-local.yaml`의 `spring.config.import` |
| 통합 실행기 | `src/test/java/site/omagotchi/learningservice/E2eLearningServiceApplication.java` |
| 격리 실행기 | `src/test/java/site/omagotchi/learningservice/TestLearningServiceApplication.java` |
