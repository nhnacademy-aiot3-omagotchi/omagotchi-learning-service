# Learning Service

교육 기수, 학습, 출결, 공간·팀과 게이미피케이션 데이터를 소유하는 서비스입니다. 현재 기반에는 PostgreSQL 18.1, Flyway와 교육 기수·소속 Migration이 포함되어 있습니다.

## 빠른 검증

Java 21과 Docker가 실행 중인 상태에서 다음 명령을 사용합니다. Testcontainers가 임시 PostgreSQL 18.1을 준비하므로 로컬 DB가 필요하지 않습니다.

```bash
./mvnw verify
```

테스트는 Flyway V1·V2 적용, `learning_service.cohorts` 생성, 계정 논리 참조 컬럼의 UUID 타입과 PostgreSQL 버전을 확인합니다.

## 일반 애플리케이션 실행

`local` profile은 저장소 루트의 `.env.local`을 읽습니다.

```bash
cp .env.local.example .env.local
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

`.env.local`의 DB 접속값은 개인 로컬 PostgreSQL 또는 팀 Compose 환경에 맞게 설정합니다. 학교 DB는 데이터베이스 이름과 `learning_service` schema 생성 권한을 확인하기 전까지 연결하지 않습니다.

Redis Presence 기능을 사용하는 현재 애플리케이션은 로컬 Redis도 필요합니다. `REDIS_HOST`와 `REDIS_PORT`는 필수이며, 인증 없는 Redis는 username/password를 생략할 수 있습니다. 연결 timeout과 SSL은 필요할 때만 `.env.local`에서 기본값을 덮어씁니다.

### 로컬 JWT 공개키

`identity-service`와 `learning-service`가 같은 상위 디렉터리에 있다고 가정합니다. Identity에서 로컬 RSA key pair를 한 번 생성하면 Learning은 private key를 복사하지 않고 다음 public key를 직접 참조합니다.

```text
../identity-service/secrets/jwt-public.pem
```

아직 로컬 key pair가 없다면 `identity-service` 저장소에서 생성합니다.

```bash
mkdir -p secrets
chmod 700 secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
chmod 600 secrets/jwt-private.pem secrets/jwt-public.pem
```

`local` profile의 기본 공개키 경로와 `.env.local.example`은 이 파일을 가리킵니다. 경로를 바꿔야 할 때만 `.env.local`의 `JWT_PUBLIC_KEY_LOCATION`을 변경합니다.

## 환경별 설정 계약

- `local`: 필수 `./.env.local`을 읽고 Eureka는 기본적으로 비활성화합니다.
- `test`: `.env.local`을 읽지 않고 `application-test.yaml`, Testcontainers PostgreSQL과 테스트 RSA Key만 사용합니다.
- `prod`: 별도 env 파일을 import하지 않습니다. DB·Redis·Eureka·JWT·Telegram·첨부파일 저장소 설정은 환경변수와 Mount된 공개키로 주입해야 합니다.

공통 `application.yaml`에는 운영 필수값의 fallback이 없습니다. 값 누락, 잘못된 Duration 또는 읽을 수 없는 JWT 공개키는 요청을 받기 전에 애플리케이션 시작을 실패시킵니다. 실제 Credential과 Key 파일은 `.env.local.example`이나 Git에 기록하지 않습니다.

## Migration 규칙

실행 SQL은 `src/main/resources/db/migration/`에서 관리합니다. 공유 브랜치나 공용 환경에 적용된 파일은 수정하지 않고, 변경이 필요하면 다음 버전의 Migration을 추가합니다. Entity는 테이블을 만들지 않으며 `ddl-auto: validate`로 Migration 결과와 일치하는지만 검사합니다.

다른 서비스가 소유한 데이터에는 Foreign Key와 JPA 연관관계를 만들지 않습니다. Identity의 사용자는 JWT `sub`와 같은 UUID `userId` 값으로만 논리 참조합니다. `cohort_memberships.id`처럼 Learning 내부 관계에 사용하는 PK는 기존 `BIGINT`를 유지합니다.
