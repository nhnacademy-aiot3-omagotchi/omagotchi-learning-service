# Learning Service 백엔드 — 개인 기여 포트폴리오

> 프로젝트: **Omagotchi** (NHN Academy AIoT 3기 팀 프로젝트) / MSA 중 **Learning Service** 담당
> 저장소: `nhnacademy-aiot3-omagotchi/omagotchi-learning-service`
> 기간: **2026-07-21 ~ 2026-08-26** (약 5주)
> 기여자 식별: git author `m00n <mjm3204@naver.com>` 기준으로 추출한 본인 커밋만 정리

---

# PART 1. 요약본 (이력서 첨부용)

## 1.1 한 줄 소개

Spring Boot 기반 MSA 환경에서 **교육 기수·출결·게이미피케이션·커뮤니티·실시간 재실(Presence)** 도메인을 설계부터 구현·테스트·문서화까지 단독 담당한 백엔드 개발자.

## 1.2 정량 지표

| 항목 | 수치 |
| --- | --- |
| 커밋 | **107개** (머지 포함) / 비-머지 79개 |
| 코드 변경량 | **+27,751 / -2,056 lines** |
| 신규 생성 파일 | **393개** (main Java 303, test Java 52, SQL·문서 38) |
| 설계·구현 도메인 | **cohort / attendance / gamification / community / realtime / telegram / ranking / user** (8개) |
| REST 엔드포인트 | **51개** (직접 작성한 컨트롤러 9종 기준) |
| 테스트 | 테스트 클래스 **49개**, 테스트 메서드 **181개** (단위·통합·IT) |
| DB 마이그레이션 | Flyway 스크립트 **21개** 작성 |
| 기술 문서 | **8건** (E2E 가이드, 런타임 가이드, Frontend 연동 인수인계 933줄 등) |

## 1.3 담당 범위 요약

- **기수(Cohort) 도메인 전체**: 기수 CRUD, 가입 코드 발급/폐기, 참가 신청·승인·반려, 소속 역할 관리, 기수 관리자 배정, 출결 정책, 감사 로그, 시스템 관리자 기수 조회·삭제 API
- **출결(Attendance) 도메인 전체**: 체크인/체크아웃, 지각·조퇴·이석 판정 정책, 관리자 최종 상태 보정 + 변경 이력, 기간별 조회 및 페이지네이션
- **게이미피케이션 도메인 전체**: 캐릭터 온보딩·성장, 레벨/전직 계산, XP 원장(Ledger), 일일 퀘스트, Outbox 기반 이벤트 처리, 닉네임 정책·중복 방지
- **커뮤니티 도메인 전체**: 게시글 CRUD·고정·검색, 이미지 첨부 저장소(파일 헤더 기반 MIME 검증), 소속 기반 다운로드 권한 검증
- **실시간 Presence**: STOMP WebSocket 인증/구독 인가 인터셉터, Redis 기반 다중 세션 Presence, 스냅샷 브로드캐스트
- **Telegram 연동**: 계정 연동 토큰(SHA-256 해시 저장), Webhook 수신, 출결 리마인더 멱등 생성
- **품질·인프라**: CodeRabbit AI 코드리뷰 도입 및 설정, Testcontainers 기반 E2E 실행기, `mvn verify` 파이프라인 정상화, 팀 전체용 런타임·E2E·API 연동 문서 작성

## 1.4 핵심 성과 5가지

1. **동시성 방어 설계** — PostgreSQL Advisory Lock, 비관적 락(`FOR UPDATE`), `SKIP LOCKED`, 부분 유니크 인덱스를 상황별로 구분 적용해 XP 중복 지급·출결 중복 기록·기수 관리자 기간 중복 배정을 구조적으로 차단.
2. **Outbox + Receipt 이중 멱등 파이프라인** — 출석/학습 완료 이벤트가 유실되지도, 두 번 반영되지도 않도록 트랜잭셔널 아웃박스와 수신 영수증(receipt) 테이블을 조합하고 재시도 스케줄러까지 구현.
3. **무중단 스키마 변경** — `CREATE INDEX CONCURRENTLY`를 Flyway `executeInTransaction=false` 설정으로 안전하게 적용, 팀 공유 DB에서 락 없이 인덱스를 추가.
4. **테스트 181개로 도메인 규칙 고정** — 시간 의존 로직에 `Clock`/`DateTimeProvider`/`Ticker`를 주입해 지각·조퇴·연속 출석 같은 경계값을 결정론적으로 검증.
5. **팀 협업 자산 구축** — Frontend 팀에 넘길 API 계약 문서(933줄), BFF 요청 흐름 설명서, E2E 실행 가이드, 런타임 장애 진단 가이드를 직접 작성해 연동 병목을 제거.

## 1.5 사용 기술

`Java 21` `Spring Boot 4.1` `Spring Security / OAuth2 Resource Server` `Spring Data JPA` `JdbcTemplate`
`PostgreSQL 18` `Flyway` `Redis` `RabbitMQ` `STOMP WebSocket` `Eureka`
`JUnit 5` `Mockito` `Testcontainers` `Maven` `Docker` `GitHub Actions` `CodeRabbit`

---

# PART 2. 상세본 (기술 심층)

## 2.1 프로젝트 개요

Omagotchi는 교육 기수 운영·학습 관리·IoT 센서 기반 공간 관리를 결합한 MSA 프로젝트다.
그중 **Learning Service**는 기수·학습·출결·공간·팀·게이미피케이션 데이터의 소유 서비스이며,
Identity Service(인증), Rule Service(센서 임계치), Frontend BFF, Gateway와 연동된다.

본인은 이 서비스 내부에서 **사용자 참여 축(기수·출결·게이미피케이션·커뮤니티·Presence)** 을 담당했다.

### 계층 구조 원칙

```
presentation → application → domain
                    ↕
              infrastructure
```

- `domain`: 순수 계산 로직과 엔티티. 프레임워크 의존 없음 (`AttendanceDecisionPolicy`, `LevelCalculator` 등 정적 정책 클래스)
- `application`: 트랜잭션 경계, 유스케이스 조립, port 인터페이스 정의
- `infrastructure`: JPA/JDBC/Redis 어댑터. application의 port를 구현
- `presentation`: HTTP·STOMP 진입점, 요청/응답 DTO 변환

---

## 2.2 기수(Cohort) 도메인

**직접 생성한 파일 87개** — 도메인 9, application 33, infrastructure 14, presentation 21 외

### 2.2.1 가입 코드: 원문 미저장 설계

`JoinCodeService` / `JoinCodeHash`

- `SecureRandom` 16바이트 → HEX 32자 코드 생성
- DB에는 **SHA-256 해시만 저장**, 원문은 발급 응답에서 **단 1회만** 반환
- 재발급 시 기존 ACTIVE 코드를 즉시 `revoke()` 처리해 유효 코드가 항상 1개임을 보장
- 만료 시각이 없거나 과거면 발급 거부, 종료된 기수(`CLOSED`)에서는 발급 자체를 차단

```java
String rawCode = generateRawCode();
CohortJoinCode joinCode = CohortJoinCode.issue(
        cohortId, JoinCodeHash.sha256(rawCode), command.expiresAt(), issuedByUserId
);
// 저장은 해시, 반환은 원문 — 조회 API는 메타데이터만 응답한다
return IssuedJoinCodeResponse.from(joinCodeRepository.save(joinCode), rawCode);
```

**설계 의도**: 가입 코드는 비밀번호와 같은 성격의 자격 증명이다. DB 유출 시에도 코드가 그대로 노출되지 않도록 단방향 해시로 저장했고, 조회 API는 발급 시각·만료 시각 같은 메타데이터만 응답하도록 계약을 분리했다.

### 2.2.2 기수 관리자 배정: Advisory Lock으로 기간 중복 차단

`CohortManagerAssignmentPolicy` / `PostgreSqlCohortManagerAssignmentLock`

한 사용자가 **운영 기간이 겹치는 두 기수의 관리자로 동시 배정되는 것**을 막아야 했다.
단순 `SELECT → 검사 → INSERT`는 동시 요청에서 Race Condition이 발생한다.

```java
entityManager.createNativeQuery("""
        SELECT pg_advisory_xact_lock(
            CAST(:namespace AS INTEGER),
            hashtext(CAST(:key AS TEXT))
        )
        """)
```

- **네임스페이스 분리**: cohort 락(`0x434F484F` = "COHO")과 user 락(`0x55534552` = "USER")을 다른 네임스페이스로 두어 서로 다른 자원의 해시 충돌을 방지
- **`pg_advisory_xact_lock`** 사용 — 트랜잭션 종료 시 자동 해제되므로 락 누수(leak)가 구조적으로 불가능
- 활성 트랜잭션이 없으면 `IllegalStateException`을 던져 잘못된 호출을 조기 실패시킴
- 락 순서를 `cohort → user`로 **고정**해 데드락 회피
- 종료일과 다음 시작일이 같은 경계는 "겹치지 않음"으로 정의 (경계값 규칙 명시)

### 2.2.3 권한 검사: 404와 403의 의도적 분리

`CohortAccessService`

```java
// ACTIVE 소속이 없으면 기수 존재 자체를 숨기기 위해 404로 처리
public CohortMembership requireActiveMembership(Long cohortId, UUID userId) { ... }

// 소속은 있으나 관리자가 아니면 403
public void requireManager(Long cohortId, UUID userId) {
    requireActiveMembershipId(cohortId, userId);   // 없으면 404
    if (!isManager(cohortId, userId)) {            // 있으나 권한 부족이면 403
        throw new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED);
    }
}
```

**설계 의도**: 403을 그대로 노출하면 "그 기수는 존재한다"는 정보가 새어나간다(열거 공격 표면). 소속이 없는 사용자에게는 404, 소속은 있으나 권한이 부족한 사용자에게만 403을 준다. 예외를 던지는 `require*`와 단순 분기용 `is*`를 분리해 호출부가 응답 코드를 의식적으로 선택하도록 API를 설계했다.

### 2.2.4 그 외

- **감사 로그**(`CohortAuditLogService`): 기수 상태 변경, 소속 승인/반려 등 관리자 행위를 별도 테이블에 기록
- **시스템 관리자 API**: 전체 기수 요약 조회, 기수 삭제(연관 데이터 정리 포함) — `CohortDeletionIT`로 통합 검증
- **출결 정책 관리**(`CohortAttendancePolicyService`): 기수별 시작/종료 시각, 허용 이석 시간, 타임존을 정책 엔티티로 분리

---

## 2.3 출결(Attendance) 도메인

**직접 생성한 파일 22개**

### 2.3.1 출결 판정 정책의 순수 함수화

`AttendanceDecisionPolicy` — 프레임워크 의존이 전혀 없는 정적 정책 클래스

| 조건 | 결과 상태 |
| --- | --- |
| 지각 0분, 조퇴 0분 | `PRESENT` |
| 지각 > 0 | `LATE` |
| 조퇴 > 0 | `LEFT_EARLY` |
| 지각·조퇴 동시 | `LATE_LEFT_EARLY` |
| 체크인/체크아웃 누락 | `ABSENT` |

**엣지 케이스 처리**

- **분 단위 올림**: `ceilToMinutes()` — 나노초가 1이라도 있으면 1분으로 올림. 59초 지각을 "지각 아님"으로 처리하지 않기 위함
- **이석(AWAY) 검증**: 복귀하지 않은 이석(`endedAt == null`), 종료 시각 이후 복귀, 허용 시간 초과 이석을 각각 조퇴로 환산
- **타임존**: 정책에 저장된 `ZoneId`로 `Instant`를 변환해 판정 — UTC 저장/로컬 판정 원칙 유지
- **경계 조건**: `!checkedInTime.isAfter(start)`로 정시 도착을 지각에서 제외 (`>` 가 아닌 `>=` 실수 방지)

### 2.3.2 체크인/체크아웃 동시성 방어

`AttendanceService`

```java
CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);
lockActiveMembership(membership.getId());          // 소속 행 선점
AttendanceRecord record = attendanceRecordRepository
        .findWithLockByCohortMembershipIdAndAttendanceDate(...)   // 비관적 락
        .orElseGet(() -> AttendanceRecord.start(...));

if (record.getCheckedInAt() != null) {
    return AttendanceRecordResult.from(record);    // 중복 체크인 = 멱등 응답
}
```

- 더블 클릭·재시도로 인한 **중복 체크인은 예외가 아니라 기존 기록 반환**(멱등)으로 처리 — 클라이언트 재시도가 안전해짐
- 체크아웃은 체크인 부재 시 명시적 예외, 이미 체크아웃 상태면 멱등 반환으로 구분
- 판정 완료 시점에 열려 있는 `PresenceInterval`을 함께 닫아 재실 구간 데이터 정합성 유지
- 체크인 성공 시 `AttendanceCheckedInEvent` 발행 → 게이미피케이션 Outbox로 연결

### 2.3.3 관리자 상태 보정 + 변경 이력

- 관리자만 최종 상태를 덮어쓸 수 있고(`requireManager`), **사유(reason)가 비어 있으면 거부**
- 이전 상태 → 다음 상태 → 사유 → requestId를 `AttendanceChangeLog`에 기록해 감사 추적 확보

### 2.3.4 조회

- 본인 기록 / 관리자 일자별 전체 기록 조회 API
- `AttendancePageQuery`로 page·size·기간 파라미터를 값 객체화하고 별도 단위 테스트로 검증

---

## 2.4 게이미피케이션 도메인 (가장 복잡했던 영역)

**직접 생성한 파일 69개** (main) + 테스트 17개

### 2.4.1 Outbox + Receipt 이중 멱등 파이프라인

출석·학습 완료 이벤트가 캐릭터 경험치와 퀘스트에 반영되어야 하는데,
**유실되어도 안 되고 두 번 반영되어도 안 되는** 요구가 있었다.

```
[출석 트랜잭션]                    [비동기 처리]                 [적용]
AttendanceService                GamificationEventRetry        DailyQuestService
      │ publish                  Coordinator.dispatch()        XpRewardService
      ▼                                 │                            ▲
GamificationEventOutboxService          │ lockPending                │
  enqueue (MANDATORY 전파)              │ FOR UPDATE SKIP LOCKED     │
      │                                 ▼                            │
      ▼                          GamificationEventProcessor ─────────┘
gamification_event_outbox        (REQUIRES_NEW + receipt.claim)
  ON CONFLICT DO NOTHING
```

**3중 방어선**

1. **Outbox 삽입**: `ON CONFLICT (event_type, source_id) DO NOTHING` — 같은 원본 이벤트는 큐에 한 번만 들어감
2. **Receipt claim**: 처리 직전 `gamification_event_receipts`에 INSERT 시도, `inserted == 1`일 때만 실제 처리 진행. 이미 처리된 이벤트는 조용히 무시
3. **XP 원장 조회**: `XpRewardService`가 `(sourceType, sourceId)`로 기존 트랜잭션을 먼저 찾아 재지급을 차단

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void process(GamificationEventMessage event) {
    boolean claimed = eventReceiptRepository.claim(...);
    if (!claimed) { return; }   // 이미 처리된 이벤트
    switch (event.eventType()) { ... }
}
```

**전파 속성 선택 이유**

- `enqueue`는 `Propagation.MANDATORY` — 반드시 출석 트랜잭션 안에서만 호출되도록 강제. 트랜잭션 밖 호출은 즉시 실패시켜 "커밋됐는데 이벤트가 없는" 상황을 컴파일 이후 단계에서라도 차단
- `process`는 `REQUIRES_NEW` — 한 이벤트 처리 실패가 다른 이벤트나 호출자 트랜잭션을 롤백시키지 않도록 격리

**재시도**

- 실패 시 `attempt_count` 증가 + `next_attempt_at` 지연 + `last_error` 저장
- `GamificationEventRetryScheduler`가 주기적으로 `findRetryable()` 호출
- `FOR UPDATE SKIP LOCKED`로 다중 인스턴스 환경에서도 같은 이벤트를 중복 처리하지 않음

### 2.4.2 XP 원장(Ledger)과 전직 이력

`XpRewardService`

```java
// EXP와 레벨은 user_character 한 행에 모이므로 지급 시점에 잠금
UserCharacter character = userCharacterQueryRepository.getForUpdate(representative.getId());
...
LevelState levelState = character.addXp(amount, policies);
// 한 번에 여러 전직 구간을 넘는 보상도 빠진 이력 없이 남김
saveAdvancementHistories(character, previousStage, levelState, transaction.getId());
```

- **원장 방식**: 캐릭터의 누적 XP만 갱신하는 대신 `xp_transactions`에 지급 건마다 행을 남겨 추적 가능성 확보
- **비관적 락**: XP와 레벨이 한 행에 모이므로 동시 지급 시 lost update가 발생 — `getForUpdate`로 직렬화
- **다단계 전직 처리**: 대량 XP 한 번으로 BASE → FIRST → SECOND를 건너뛸 때, 중간 단계 이력이 누락되지 않도록 `AdvancementStage`를 순회하며 빠짐없이 기록

### 2.4.3 도메인 계산 로직 분리

| 클래스 | 책임 |
| --- | --- |
| `LevelCalculator` | 정책 목록 기반 레벨 계산, 최대 30레벨 고정, 다음 레벨 요구치 산출 |
| `AdvancementCalculator` | 레벨 → 전직 단계 매핑 (10/20/30) |
| `WeekdayStreakCalculator` | **주말 건너뛰기** 연속 출석 계산 — 토·일은 카운트도 중단도 하지 않고 스킵 |
| `StudyProgressionCalculator` | 학습 시간 기반 진척도 |
| `CharacterNicknameValidator` | 닉네임 정책 |

`WeekdayStreakCalculator`는 "금요일에 출석하고 월요일에 출석하면 연속 2일"이라는 요구를 만족시키기 위해 주말을 커서 이동만 하고 판정에서 제외하도록 구현했다.

### 2.4.4 닉네임 정책: 우회 시도까지 방어

`CharacterNicknameValidator`

```java
String withoutDigits     = collapseRepeated(lowercase.replaceAll("[0-9]", ""));
String phoneticNormalized = withoutDigits.replace("시이", "시").replace("씨이", "씨")...;
String leetNormalized    = collapseRepeated(lowercase
        .replace('0','o').replace('1','i').replace('3','e')
        .replace('4','a').replace('5','s').replace('7','t'));
```

- **NFKC 정규화**로 유니코드 호환 문자 우회 차단
- **반복 문자 축약**(`collapseRepeated`) — "시이이이발" 같은 늘려쓰기 우회 차단
- **Leet 변환** — `5h1t` 같은 숫자 치환 우회 차단
- **음성학적 정규화** — 한글 늘려쓰기 패턴 정규화
- 외부 금칙어 라이브러리 + 자체 예약어(`admin`, `관리자`, `운영자` 등) 이중 검사
- 4가지 정규화 결과를 **모두** 검사해 단일 정규화로는 막히지 않는 조합 우회를 차단

### 2.4.5 닉네임 중복: 부분 유니크 인덱스 + 제약 위반 번역

```sql
CREATE UNIQUE INDEX CONCURRENTLY ux_user_characters_representative_nickname
    ON learning_service.user_characters (LOWER(nickname))
    WHERE is_representative;
```

- **부분 인덱스**: 대표 캐릭터만 닉네임이 유일해야 하고 보조 캐릭터는 제약 대상이 아님
- **`LOWER(nickname)`**: 대소문자 차이만 있는 닉네임을 같은 것으로 취급
- **`CONCURRENTLY`**: 팀 공유 DB에 락 없이 인덱스 추가 → 트랜잭션 안에서 실행 불가하므로 Flyway `.sql.conf`에 `executeInTransaction=false` 명시 (`.conf` 파일에 삭제 금지 주석까지 기재)

애플리케이션에서는 `UserCharacterConstraintTranslator`가 `DataIntegrityViolationException`의 인덱스명을 읽어 **409 중복 닉네임** 비즈니스 예외로 번역한다.

```java
/**
 * 던지지 않고 반환한다. 호출부에서 throw translate(e)로 쓰면
 * 컴파일러가 그 지점에서 흐름이 끝난다는 것을 안다.
 * 인덱스명을 못 읽으면 원본을 그대로 돌려준다. 엉뚱한 409로 오진하면 조용히 묻히지만,
 * 500이 나가면 stack trace가 남아 원인을 찾을 수 있다.
 */
```

**설계 의도**: 선(先)조회 후(後)삽입 방식은 동시 요청에서 뚫린다. DB 제약을 단일 진실 공급원으로 두고 위반을 예외로 번역하는 방식이 경쟁 조건에 안전하다. 인덱스명을 읽지 못하면 500을 내보내도록 한 것은 **잘못된 409로 오진해 원인을 묻어버리는 것보다 stack trace를 남기는 편이 낫다**는 판단이다.

### 2.4.6 일일 퀘스트

`DailyQuestService` — 5종 퀘스트(출석 / 학습 완료 / 캐릭터 확인 / 루틴 리뷰 / LLM 퀘스트)

- **보상 수령 시 행 잠금**: `findWithLockById`로 이중 수령 차단
- **상태 머신**: `PENDING → COMPLETED → CLAIMED`, 지난 날짜는 `EXPIRED`
- 만료 처리를 `expirePastQuests()` 배치로 분리하고, 수령 API는 날짜 검사만 수행해 **책임을 나눔**
- 수령 → 원장 생성 → XP 지급이 **하나의 트랜잭션**에서 끝나도록 경계 설정
- 클라이언트가 임의로 완료 처리할 수 없도록 **서버 검증 기반 완료**로 전환 (`fix: 서버 검증 기반 일일 퀘스트 완료 처리`)

---

## 2.5 커뮤니티 도메인

**직접 생성한 파일 34개**

### 2.5.1 첨부파일 저장소: 파일명을 신뢰하지 않는 설계

`LocalCommunityAttachmentStorage`

```java
private String safeOriginalFileName(String originalFileName) {
    if (originalFileName == null || originalFileName.isBlank()
            || originalFileName.contains("/")  || originalFileName.contains("\\")
            || originalFileName.contains("\0") || originalFileName.contains("..")) {
        throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
    }
    return originalFileName.trim();
}
```

- **Path Traversal 차단**: `..`, 경로 구분자, 널바이트 검사
- **클라이언트 파일명을 저장 경로로 사용하지 않음** — 서버가 생성한 `storageKey`(UUID + 날짜 디렉터리)로 저장
- **매직 넘버 검증**: 확장자만 믿지 않고 파일 헤더를 읽어 실제 MIME을 판별한 뒤 확장자와 **교차 검증**
- 허용 목록 방식(jpeg/png/gif)과 최대 크기 제한
- 다운로드 API는 **게시글 소속 기수 기준 접근 권한 검증**(`feat: community 첨부파일 다운로드 API 접근 권한 게시글 소속 검증`)

### 2.5.2 CQRS 스타일 분리

- `CommunityPostCommandService` (쓰기) / `CommunityPostQueryService` (읽기)를 분리
- 조회는 `CommunityPostQueryPort` → `CommunityPostQueryJpaAdapter`로 별도 어댑터 구성, 목록/상세 전용 프로젝션 사용
- 게시글 타입(`CommunityPostType`)·공개 범위(`CommunityPostScope`)·고정(pin) 기능

---

## 2.6 실시간 Presence (WebSocket + Redis)

**직접 생성한 파일 18개**

### 2.6.1 STOMP 인증·인가 인터셉터

| 인터셉터 | 시점 | 역할 |
| --- | --- | --- |
| `WebSocketConnectAuthenticationInterceptor` | `CONNECT` | Bearer JWT를 기존 resource-server의 `JwtDecoder`로 검증 → WebSocket Principal 연결 → Presence 세션 등록 |
| `WebSocketSubscribeAuthorizationInterceptor` | `SUBSCRIBE` | 구독 destination을 파싱해 **해당 기수의 ACTIVE 소속일 때만** 허용 |

**설계 의도**: HTTP 계층의 Spring Security 설정은 WebSocket 프레임에 자동 적용되지 않는다. 그렇다고 별도 검증 로직을 새로 만들면 정책이 두 벌이 되므로, 기존 `JwtDecoder`와 `JwtAuthenticationConverter` Bean을 **재사용**해 인증 정책을 한 곳으로 유지했다.
구독 인가를 클라이언트가 보내는 payload가 아니라 **서버가 destination에서 파싱한 cohortId**로 검사하기 때문에, 클라이언트가 남의 기수 토픽을 구독하는 것이 불가능하다.

### 2.6.2 Redis 다중 세션 Presence

```
presence:session:{sessionId}   → Hash(userId, cohortId)  + TTL
presence:user:{userId}:sessions → Set(sessionId…)
presence:cohort:{cohortId}      → Set(userId…)
```

- **세션 해시에만 TTL**을 두고, 만료된 해시는 스냅샷 조회 시점에 사용자 세션 Set에서 정리(lazy cleanup)
- 한 사용자의 **다중 탭·다중 기기**를 세션 Set으로 관리 — 탭 하나를 닫아도 다른 탭이 살아 있으면 온라인 유지
- 기수 이동 시 이전 기수 Set에서 제거(`removeFromPreviousCohortIfChanged`)
- 상태 변경 시 STOMP 토픽으로 스냅샷 브로드캐스트

### 2.6.3 세션 소유권 검증

```java
// REST 경로에서는 요청자가 sessionId를 실어 보내므로 소유자 대조가 없으면
// 남의 재실 세션을 강제로 종료시킬 수 있다.
if (requesterId != null && !session.userId().equals(requesterId)) {
    throw new BusinessException(PresenceErrorCode.SESSION_ACCESS_DENIED);
}
```

REST heartbeat/disconnect는 클라이언트가 `sessionId`를 직접 전달하므로, 소유자 대조 없이는 **타인의 재실 세션을 강제 종료시킬 수 있는 취약점**이 된다. STOMP disconnect 이벤트에서는 Principal이 없어 `null`이 들어오므로, `null`일 때만 검증을 건너뛰도록 분기해 기존 동작을 깨지 않으면서 REST 경로를 방어했다.

또한 `AccessDeniedException` 대신 `BusinessException`을 던지는데, 이는 `GlobalExceptionHandler`의 catch-all이 `AccessDeniedException`을 500으로 바꿔버리기 때문이다 — **프레임워크 예외 처리 흐름까지 확인하고 선택한 예외 타입**이다.

---

## 2.7 Telegram 연동

**직접 생성한 파일 31개**

- **연동 토큰**: `TelegramTokenHash.sha256()` — 가입 코드와 동일하게 원문 미저장
- **Webhook**: `POST /api/v1/webhooks/telegram` — Access JWT 예외 경로로 분리하되 제공자 검증은 별도로 수행
- **출결 리마인더 멱등 생성**: `(소속, 날짜, 알림유형, 채널)` 조합당 1건만 유지

```java
try {
    return AttendanceReminderResponse.from(reminderRepository.save(reminder));
} catch (DataIntegrityViolationException exception) {
    // 스케줄러 중복 실행 시 유니크 제약에 걸리면 기존 행을 재조회해 반환
    return reminderRepository.findBy...(...)
            .map(AttendanceReminderResponse::from)
            .orElseThrow(() -> exception);
}
```

**설계 의도**: 조회 후 없으면 삽입하는 방식은 스케줄러가 중복 실행되는 순간 유니크 제약에 걸린다. 제약 위반을 잡아 재조회하는 패턴으로 경쟁 조건에서도 멱등성을 보장했고, 재조회마저 실패하면 원본 예외를 던져 진짜 문제를 숨기지 않는다.

---

## 2.8 사용자 프로필 / 랭킹

- `UserProfileService`: 프로필 요약(승인된 기수, 현재 대표 캐릭터), 닉네임 변경
- `RankingCalculator` / `RankingSnapshotService`: 경쟁 랭킹(동점 처리) 계산과 스냅샷 저장 (이후 팀 리팩터링으로 구조 변경)

---

## 2.9 데이터베이스 설계

Flyway 마이그레이션 **21개** 작성. 직접 스키마를 설계한 테이블 그룹:

> 참고: 프로젝트 후반에 팀 차원에서 V1–V8 통합 기준선(baseline)으로 마이그레이션을 재정리했다.
> 아래 테이블들의 최초 스키마는 본인이 작성한 마이그레이션에서 나왔으며, 현재 저장소에는 통합 기준선에 흡수되어 있다.
> 통합 이후 본인이 추가한 마이그레이션(V9~V15)은 원본 파일 그대로 남아 있다.

| 그룹 | 테이블 |
| --- | --- |
| 기수 | `cohorts`, `cohort_memberships`, `cohort_join_codes`, `cohort_attendance_policies`, `cohort_audit_logs` |
| 출결 | `attendance_records`, `attendance_change_logs`, `presence_intervals` |
| 게이미피케이션 | `game_characters`, `user_characters`, `level_policies`, `xp_transactions`, `advancement_histories`, `quest_templates`, `user_daily_quests`, `gamification_event_outbox`, `gamification_event_receipts` |
| 커뮤니티 | `community_posts`, `community_post_attachments` |
| 알림 | `telegram_user_links`, `telegram_link_tokens`, `attendance_reminders` |

### 운영 원칙

- **서비스 간 참조는 FK 대신 논리 식별자** — Identity Service의 사용자는 JWT `sub`와 동일한 UUID로만 참조 (MSA 경계 유지)
- **`ddl-auto=validate`** — 스키마 변경은 반드시 마이그레이션을 통해서만
- **JDBC 시간대 UTC 고정**, 판정 시점에만 정책 타임존으로 변환
- 적용 완료된 마이그레이션은 수정 금지, 변경은 항상 새 버전 추가
- 누락된 외래키 인덱스 보강(`V16__add_missing_foreign_key_indexes.sql`, 현재는 통합 기준선에 흡수) — 조인 성능 문제를 사전 차단

---

## 2.10 테스트 전략

**테스트 클래스 49개 / 테스트 메서드 181개** 작성

### 계층별 전략

| 유형 | 대상 | 예시 |
| --- | --- | --- |
| 도메인 단위 테스트 | 순수 계산 로직 | `AttendanceDecisionPolicyTest`, `CharacterNicknameValidatorTest` |
| 서비스 단위 테스트 | Mockito 기반 유스케이스 | `XpRewardServiceTest`, `DailyQuestServiceTest`, `CohortMembershipServiceTest` |
| 컨트롤러 테스트 | 인증·검증·응답 계약 | `CohortControllerTest`, `GamificationControllerTest` |
| 통합 테스트(IT) | Testcontainers PostgreSQL | `CohortDeletionIT`, `GamificationReferenceDataBootstrapIT`, `CohortManagerPeriodRepositoryIT` |
| E2E 시나리오 | 도메인 횡단 전체 흐름 | `SystemAdminCohortAttendanceQuestFlowIT` |

### 시간 의존성 제거

시간 기반 로직(지각 판정, 연속 출석, 퀘스트 만료, 이벤트 재시도)은 실제 시각에 의존하면 테스트가 불안정해진다.

- `Clock`을 Bean으로 주입해 서비스에서 `clock.instant()` 사용
- `DateTimeProvider` / `DateTimePolicy`로 집계 기준일(aggregation date) 계산을 한 곳에 모음
- 만료 동작 테스트를 위해 `Ticker`를 주입하되 **기본 생성자로 기존 호환 유지** (`refactor: 만료 동작 테스트에서 검증 위해서 Ticker 주입`)

### E2E 시나리오 테스트

`SystemAdminCohortAttendanceQuestFlowIT` — **SYSTEM_ADMIN 기수 생성 → 관리자 배정 → 가입 코드 발급 → 학생 참가 신청 → 승인 → 출결 정책 설정 → 캐릭터 온보딩 → 체크인 → 일일 퀘스트 완료** 까지 도메인 6개를 관통하는 단일 시나리오를 하나의 테스트로 검증.

### E2E 실행기

`E2eLearningServiceApplication` — Testcontainers PostgreSQL과 함께 실제 서비스를 띄워 Frontend BFF·Gateway 연동을 확인하는 실행기.

```java
SpringApplication.from(LearningServiceApplication::main)
        .with(E2eTestcontainersConfiguration.class)
        .run(args);
```

로컬 PostgreSQL 설치 없이도 다른 팀이 연동 테스트를 할 수 있게 되어, **환경 구축 때문에 연동이 막히는 상황을 제거**했다.

---

## 2.11 코드 품질 · 협업

### CodeRabbit AI 코드 리뷰 도입

`.coderabbit.yaml` (279줄) 직접 작성 및 팀 도입

- 한국어 리뷰, `tone_instructions`로 **"왜 문제인지 원인까지 설명 + 레퍼런스 제안 + 함께 성장하는 동료 톤"** 정의
- PR 요약, 파일별 변경 요약, 시퀀스 다이어그램 자동 생성, 연결 이슈 분석 활성화
- 실제로 **`refactor: 코드래빗 리뷰 반영`류 커밋만 10건 이상** — 의존성 직접 참조 분리, 유니크 제약 검사 개선, 동시 요청 직렬화 보완 등 리뷰 지적을 실제 개선으로 연결

### 팀 문서화 (8건)

| 문서 | 분량 | 내용 |
| --- | --- | --- |
| `Frontend-Learning-API-Integration-Handoff.md` | 933줄 | 도메인 8개 전체 API 계약, 요청/응답 스펙, BFF 구현 규칙, CSRF·에러 전달 정책 |
| `Frontend-BFF-Request-Flow-Guide.md` | 489줄 | BFF 개념부터 실제 요청 흐름까지 비유를 들어 설명, 자주 하는 오해 5가지 정리 |
| `Learning-Service-Runtime-Guide.md` | 338줄 | HikariCP 커넥션 풀, 실행기 3종 차이, `.env.local` 누락 진단 스크립트, 증상별 원인 찾기 |
| `Frontend-Learning-Integration-Task-Brief.md` | 314줄 | Frontend 팀 작업 체크리스트, 계약 차이 목록, 구현 순서, 완료 조건 |
| `Learning-Service-E2E-Guide.md` | 263줄 | E2E 정의, 테스트 종류 차이, IntelliJ/CLI 실행 설정, 오류별 해결법 |
| `Learning-Pre-E2E-Work-Guide.md` | 171줄 | E2E 전 선행 작업 항목 정리 |

README의 서비스 개요·환경 프로파일·HTTP 경계·마이그레이션 운영 절차 섹션도 직접 작성했다.

---

## 2.12 해결한 주요 트러블슈팅

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| Flyway 마이그레이션 버전 충돌 | 여러 기능 브랜치에서 동일 버전 번호(V10~V15)를 각자 생성 | 버전 재배치 + 파일명 중복 해소, 이후 V1–V8 통합 기준선(baseline) 전환 절차를 README에 문서화 |
| `CREATE INDEX CONCURRENTLY` 실패 | PostgreSQL에서 트랜잭션 내부 실행 불가 | Flyway `.sql.conf`에 `executeInTransaction=false` 추가 + 삭제 금지 주석 명시 |
| 닉네임 길이 검증 오작동 | 12자 기준 검증이 **12자 정상 입력까지 거부** | 경계 조건 재검토 후 수정, 경계값 테스트 추가 |
| 게이미피케이션 이벤트 시각 불일치 | Outbox 저장 시 `Clock` 대신 시스템 시각 사용 | `Clock` 주입 일관화 (`fix: clock 불일치 수정`) |
| `mvn verify` 실패 | Docker 연결 실패, Testcontainers RabbitMQ 컨테이너 불필요 기동 | 테스트 컨테이너 구성 정리, RabbitMQ 제거로 빌드 시간·실패율 감소 |
| QueryDSL 관련 애플리케이션 기동 실패 | Q클래스 생성/의존성 설정 문제 | `pom.xml` 빌드 설정 수정 |
| 400이어야 할 요청이 500 응답 | 잘못된 위치의 `@Validated`로 예외 타입이 바뀜 | 어노테이션 제거 후 Bean Validation 경로로 정상화 |

---

## 2.13 회고

### 잘한 점

- **"동시에 두 번 들어오면?"을 기본 질문으로 삼은 것.** XP 지급, 출결 체크인, 퀘스트 수령, 관리자 배정, 리마인더 생성 — 상태를 바꾸는 모든 지점에서 동시성 시나리오를 먼저 검토했고, 상황에 맞는 도구(Advisory Lock / 비관적 락 / 유니크 제약 / SKIP LOCKED)를 구분해 적용했다.
- **DB 제약을 단일 진실 공급원으로 둔 것.** 애플리케이션 선검사는 UX용, 실제 보장은 제약이라는 원칙을 세우고, 제약 위반을 비즈니스 예외로 번역하는 레이어를 따로 만들었다.
- **주석에 "왜"를 남긴 것.** `UserCharacterConstraintTranslator`처럼 판단 근거와 트레이드오프까지 적어둔 주석이 이후 코드 리뷰와 인수인계에서 실제로 시간을 아꼈다.
- **문서를 코드와 같은 수준으로 취급한 것.** Frontend 연동 병목이 실제로 사라졌다.

### 아쉬운 점 · 다음에 개선할 것

- **Flyway 버전 충돌**이 여러 번 반복됐다. 브랜치 전략 단계에서 마이그레이션 번호 예약 규칙을 정했어야 했다.
- **Outbox 처리기의 관측성**이 부족하다. `last_error`는 남기지만 실패 이벤트에 대한 알림·대시보드가 없어, DLQ 개념과 메트릭 노출이 필요하다.
- **Presence의 lazy cleanup**은 스냅샷 조회가 없으면 만료 세션이 오래 남는다. 주기적 정리 배치를 병행하는 편이 안전하다.
- **통합 테스트 실행 시간**이 길어졌다. 컨텍스트 재사용과 테스트 슬라이싱을 더 적극적으로 적용할 여지가 있다.

---

## 부록. 담당 API 엔드포인트

| 컨트롤러 | Base Path | 엔드포인트 수 |
| --- | --- | --- |
| `CohortController` | `/api/v1/cohorts` | 19 |
| `CommunityPostController` | `/api/v1/community/posts` | 9 |
| `GamificationController` | `/api/v1/gamification` | 6 |
| `AttendanceController` | `/api/v1/cohorts/{cohortId}/attendance-records` | 5 |
| `TelegramController` | `/api/v1/telegram` | 4 |
| `PresenceController` | `/api/v1/cohorts/me/presence` | 3 |
| `CohortMembershipController` | `/api/v1/cohort-memberships` | 2 |
| `UserProfileController` | `/api/v1/user-profiles/me` | 2 |
| `TelegramWebhookController` | `/api/v1/webhooks/telegram` | 1 |
| **합계** | | **51** |

WebSocket: `/ws/**` 핸드셰이크, `/topic/cohorts/{cohortId}/presence` 구독, `/user/queue/notifications`
