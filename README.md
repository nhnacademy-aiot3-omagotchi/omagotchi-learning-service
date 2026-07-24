# Learning Service

교육 기수, 학습, 출결, 공간·팀과 게이미피케이션 데이터를 소유하는 서비스입니다. 현재 기반에는 PostgreSQL 18.1, Flyway와 교육 기수·소속 Migration이 포함되어 있습니다.

## 빠른 검증

Java 21과 Docker가 실행 중인 상태에서 다음 명령을 사용합니다. Testcontainers가 임시 PostgreSQL 18.1을 준비하므로 로컬 DB가 필요하지 않습니다.

```bash
./mvnw verify
```

테스트는 Flyway V1·V2 적용, `learning_service.cohorts` 생성, 계정 논리 참조 컬럼의 UUID 타입과 PostgreSQL 버전을 확인합니다.

## 일반 애플리케이션 실행

`local` profile은 저장소 루트의 `.env`를 읽습니다.

```bash
cp .env.example .env
./mvnw -Dspring-boot.run.profiles=local spring-boot:run
```

`.env`의 DB 접속값은 개인 로컬 PostgreSQL 또는 팀 Compose 환경에 맞게 설정합니다. 학교 DB는 데이터베이스 이름과 `learning_service` schema 생성 권한을 확인하기 전까지 연결하지 않습니다.

## Migration 규칙

실행 SQL은 `src/main/resources/db/migration/`에서 관리합니다. 공유 브랜치나 공용 환경에 적용된 파일은 수정하지 않고, 변경이 필요하면 다음 버전의 Migration을 추가합니다. Entity는 테이블을 만들지 않으며 `ddl-auto: validate`로 Migration 결과와 일치하는지만 검사합니다.

다른 서비스가 소유한 데이터에는 Foreign Key와 JPA 연관관계를 만들지 않습니다. Identity의 사용자는 JWT `sub`와 같은 UUID `userId` 값으로만 논리 참조합니다. `cohort_memberships.id`처럼 Learning 내부 관계에 사용하는 PK는 기존 `BIGINT`를 유지합니다.
