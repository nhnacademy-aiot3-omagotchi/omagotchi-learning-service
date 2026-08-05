# Gamification 최종 설계서

대상 모듈: `gamification`, `ranking`  
참조 모듈: `study`, `attendance`  
작성 목적: EXP, Level, 전직, 일일 퀘스트, 스트릭, 타이머 progression, 공부 시간 랭킹의 최종 정책과 구현 방향을 팀 기준으로 정리한다.

## 1. 목적

gamification은 학습 서비스 안에서 사용자의 성장감을 만드는 기능이다. 핵심은 보상 재화가 아니라 "학습 행동이 캐릭터 성장으로 누적된다"는 흐름이다.

사용자는 대표 `user_character`를 가진다. 일일 퀘스트를 완료하고 수동으로 보상을 수령하면 EXP가 지급된다. EXP는 원장에 기록되고, 원장 기준으로 중복 지급을 막는다. 누적 EXP에 따라 레벨이 재계산되고, 특정 레벨에 도달하면 자동 전직 이력이 생성된다.

ranking은 gamification에서 분리된 모듈이다. 랭킹은 공부 시간 기준으로만 계산하며, 실제 공부 시간 집계의 원천은 `study`가 가진다. ranking은 study 결과를 읽고 snapshot을 만든 뒤 조회 응답을 제공한다.

## 2. 범위

확정 범위:

- EXP 지급
- Level 계산
- Lv10, Lv20, Lv30 자동 전직
- 대표 캐릭터 기준 성장 상태
- 캐릭터 별명 `nickname`
- 일일 퀘스트 5개
- 수동 보상 수령
- 당일이 지난 미수령 보상 수령 불가
- XP 원장 기반 중복 방지
- 평일 스트릭 3일 이상
- 4h, 6h, 8h 학습 progression 응답
- 공부 시간 랭킹 DAILY/WEEKLY/MONTHLY
- ranking snapshot 저장

제외 범위:

- Point
- Order
- Achievement
- Badge
- Title
- LLM 퀘스트 상세 생성 로직
- study 타이머 누적 계산 로직
- attendance 출석 판정 로직 자체
- 캐릭터 이미지/배경 리소스 업로드 파이프라인

## 3. 핵심 정책 요약

| 영역 | 정책 |
| --- | --- |
| 성장 단위 | `user_character` |
| 대표 캐릭터 | 사용자당 1개만 대표 가능 |
| EXP 지급 | 서버 확정 이벤트 + 수동 보상 수령 시점 |
| EXP 중복 방지 | `xp_transactions(source_type, source_id)` unique |
| 레벨 | max 30, `level_policies` 테이블 기반 |
| 전직 | Lv10 FIRST, Lv20 SECOND, Lv30 THIRD |
| 퀘스트 | ROUTINE 4개 + LLM 1개 |
| 보상 | 완료 후 수동 수령 |
| 일일 보상 만료 | 날짜가 지나면 수령 불가 |
| 스트릭 | 평일 연속 출석 3일 이상, 주말 제외 |
| 타이머 | 4h/6h/8h 기준만 gamification에서 응답 |
| 랭킹 | study 공부 시간 기준, snapshot 조회 |
| 동점 | 공동 순위, 다음 순위는 건너뜀 |

## 4. 레벨 디자인

확정 정책:

- Max level은 30이다.
- 전체 성장 기간은 약 48일을 기준으로 설계한다.
- 하루 기대 획득량은 450 EXP/day 기준이다.
- 레벨 정책은 코드 상수가 아니라 `level_policies` 테이블로 관리한다.

현재 기본 정책:

```text
level N의 최소 누적 EXP = (N - 1)^2 * 100
```

예시:

| Level | min_total_xp |
| --- | ---: |
| 1 | 0 |
| 2 | 100 |
| 10 | 8,100 |
| 20 | 36,100 |
| 30 | 84,100 |

테이블 기반으로 둔 이유:

- 레벨 곡선은 운영 중 조정 가능성이 높다.
- 코드 배포 없이 DB seed/migration 정책으로 조정할 수 있다.
- "48일", "450 EXP/day" 같은 밸런싱 기준이 바뀌어도 도메인 코드를 건드리지 않는다.
- 테스트에서는 테이블 값을 주입해 경계값을 검증할 수 있다.

보류 정책:

- 실제 운영 EXP 곡선이 현재 제곱식으로 확정인지, 기획 밸런싱 후 별도 테이블 값으로 교체할지는 추후 확정한다.

## 5. XP 정책

확정 정책:

- EXP는 서버가 확정한 이벤트에 대해서만 지급한다.
- 일일 퀘스트는 완료만으로 EXP가 지급되지 않는다.
- 사용자가 보상을 수동 수령할 때 EXP를 지급한다.
- EXP 지급 기록은 `xp_transactions` 원장에 남긴다.
- 같은 원본 이벤트로 보상이 두 번 나가지 않도록 원장 unique 제약으로 막는다.

현재 source:

| source_type | source_id |
| --- | --- |
| DAILY_QUEST | `user_daily_quests.id` 문자열 |

트랜잭션 경계:

- 퀘스트 수령 상태 변경
- XP 원장 생성
- 대표 `user_character` EXP 증가
- level 재계산
- 전직 이력 생성

위 작업은 하나의 트랜잭션 안에서 처리한다.

중복 방지:

- `xp_transactions(source_type, source_id)` unique
- `user_daily_quests` row lock
- `user_characters` row lock
- `advancement_histories(user_character_id, stage)` unique

## 6. user_character 성장 구조

확정 정책:

- 성장 상태는 user가 아니라 `user_character` 단위로 저장한다.
- `user_id`와 `user_character_id`를 분리한다.
- 보상은 수령 시점의 대표 `user_character`에 지급한다.
- 대표 캐릭터는 사용자당 하나만 허용한다.

분리 이유:

- 사용자가 앞으로 여러 캐릭터를 가질 수 있다.
- 캐릭터별 성장 상태와 이력을 분리할 수 있다.
- 랭킹, 전직, XP 원장이 특정 캐릭터를 기준으로 고정된다.
- user는 계정 식별자이고, user_character는 성장 대상이다.

대표 캐릭터 정책:

- `user_characters.is_representative = true`
- partial unique index로 사용자당 대표 캐릭터 1개만 허용
- 홈, 보상 지급, 랭킹 표시 대상은 대표 캐릭터 기준

## 7. 캐릭터 이름과 nickname 정책

확정 정책:

- `game_characters.name`은 캐릭터 마스터 이름이다.
- `user_characters.nickname`은 사용자가 입력한 캐릭터 별명이다.
- 홈/카드/랭킹 표시 이름은 nickname을 우선 사용한다.
- nickname은 1~30자다.
- 저장 전 trim한다.
- trim 후 빈 값이면 저장하지 않는다.
- 중복 nickname은 허용한다.

예시:

| 컬럼 | 의미 | 예시 |
| --- | --- | --- |
| `game_characters.name` | 마스터 캐릭터 이름 | 야간반 |
| `user_characters.nickname` | 사용자 캐릭터 별명 | 춘식이 |
| displayName | 화면 표시 이름 | 춘식이 |

보류 정책:

- 금칙어
- 이모지 제한
- 특수문자 제한

검증 메서드는 추후 확장 가능하도록 분리한다.

## 8. 온보딩 정책

확정 흐름:

```text
nickname 입력 -> 캐릭터 선택 -> user_characters 생성
```

생성 기본값:

| 컬럼 | 값 |
| --- | --- |
| `total_xp` | 0 |
| `level` | 1 |
| `advancement_stage` | BASE |
| `is_representative` | true |

온보딩 시점에는 EXP 지급이나 퀘스트 수령이 발생하지 않는다.

## 9. 전직 정책

확정 정책:

- 전직은 레벨 도달 시 자동 처리한다.
- Lv10 도달: FIRST
- Lv20 도달: SECOND
- Lv30 도달: THIRD
- 초기 상태는 BASE다.
- 전직 이력은 `advancement_histories`에 저장한다.
- 같은 stage 이력은 캐릭터당 한 번만 저장한다.

전직 단계:

| Level | Stage |
| ---: | --- |
| 1~9 | BASE |
| 10~19 | FIRST |
| 20~29 | SECOND |
| 30 | THIRD |

이미지/배경 정책:

- 화면에서 캐릭터 이미지는 `advancement_stage`에 따라 다르게 매핑한다.
- 배경도 stage 또는 level 구간에 따라 변경 가능하다.
- 현재 DB에는 이미지 URL 컬럼을 두지 않는다.

보류 정책:

- 이미지 리소스 저장 위치
- stage별 이미지/배경 파일명 규칙
- 프론트 fallback 이미지 정책

## 10. 일일 퀘스트 정책

확정 정책:

- 하루 퀘스트는 총 5개다.
- ROUTINE 4개 + LLM 슬롯 1개다.
- 사용자가 해당 날짜 퀘스트를 조회하면 없을 때 생성한다.
- 같은 사용자/날짜/code 퀘스트는 중복 생성하지 않는다.
- 보상은 완료 후 수동 수령한다.
- 날짜가 지난 미수령 보상은 수령할 수 없다.
- 지난 퀘스트는 만료 처리 가능하다.

기본 템플릿:

| type | code | title | rewardXp |
| --- | --- | --- | ---: |
| ROUTINE | ATTENDANCE | 출석하기 | 20 |
| ROUTINE | STUDY_COMPLETED | 학습 완료하기 | 30 |
| ROUTINE | CHARACTER_CHECKED | 캐릭터 확인하기 | 10 |
| ROUTINE | ROUTINE_REVIEW | 오늘 학습 돌아보기 | 20 |
| LLM | LLM_QUEST | AI 추천 퀘스트 | 40 |

LLM 정책:

- LLM 슬롯은 1개만 둔다.
- LLM 상세 생성, 추천 문구, 난이도 계산은 이번 범위에서 제외한다.
- gamification은 LLM 완료 이벤트만 처리한다.

퀘스트 상태:

| status | 의미 |
| --- | --- |
| IN_PROGRESS | 진행 중 |
| COMPLETED | 완료, 보상 미수령 |
| CLAIMED | 보상 수령 완료 |
| EXPIRED | 만료 |

## 11. 스트릭 정책

확정 정책:

- 평일 연속 출석 3일 이상이면 스트릭 달성이다.
- 토요일, 일요일은 계산에서 제외한다.
- 금요일 출석 후 월요일 출석이면 주말 때문에 끊기지 않는다.
- 결석한 평일이 있으면 스트릭은 끊긴다.

참조 기준:

- 최종 정책은 attendance 확정 결과를 참조한다.
- gamification은 출석 판정을 직접 계산하지 않는다.
- attendance가 확정한 출석 결과를 읽어 progression 응답에 조립한다.

현재 구현상 주의:

- 현재 코드는 일일 퀘스트의 `ATTENDANCE` 완료/수령 상태로 스트릭을 계산한다.
- 팀 정책이 "attendance 확정 결과 기준"이면 attendance read-only 조회로 교체해야 한다.
- 이 교체는 `attendance` 코드를 수정하지 않고 gamification 쪽 read-only repository를 추가하는 방식이 적절하다.

## 12. 타이머 progression 정책

확정 정책:

- 실제 공부 시간 누적 계산은 study 담당이다.
- gamification은 study 결과를 읽어 기준 달성 여부만 응답한다.
- 기준값은 초 단위로 고정한다.

기준:

| 기준 | seconds |
| --- | ---: |
| 4h | 14,400 |
| 6h | 21,600 |
| 8h | 28,800 |

응답 의미:

- `studySeconds`: 해당 aggregation date의 누적 공부 시간
- `reachedFourHours`: 4시간 이상
- `reachedSixHours`: 6시간 이상
- `reachedEightHours`: 8시간 이상

모듈 책임:

- `study`: 타이머 기록, 누적, 보정
- `gamification`: 기준값, 달성 여부, 화면 응답 조립

## 13. 랭킹 정책

확정 정책:

- 랭킹은 공부 시간 기준이다.
- 실제 공부 시간은 study 결과를 참조한다.
- ranking은 study 테이블을 read-only로 조회한다.
- 대표 `user_character`가 있는 사용자만 랭킹 entry로 저장한다.
- display name은 대표 캐릭터 nickname 기준이다.
- DAILY, WEEKLY, MONTHLY를 지원한다.
- 응답은 top10 + myRank + generatedAt을 포함한다.
- 동점자는 공동 순위다.
- 공동 순위 다음 순위는 건너뛴다.
- 랭킹은 `ranking_snapshots`에 저장한 snapshot 기준으로 조회한다.

기간 계산:

| period | range |
| --- | --- |
| DAILY | baseDate 하루 |
| WEEKLY | baseDate가 속한 월요일~일요일 |
| MONTHLY | baseDate가 속한 월 1일~말일 |

동점 예시:

```text
A 28,800초 -> 1위
B 28,800초 -> 1위
C 21,600초 -> 3위
```

top10 해석:

- 현재 정책은 "상위 10명"이다.
- 따라서 공동 10위가 여러 명이어도 응답은 10명으로 제한한다.

보류 정책:

- "상위 10명"이 아니라 "10위까지 전원"으로 바꿀지 여부
- 0초 사용자를 랭킹에 노출할지 여부
- cohort 탈퇴/비활성 사용자의 과거 snapshot 노출 정책

## 14. ERD 요약

최종 테이블:

- `game_characters`
- `user_characters`
- `level_policies`
- `quest_templates`
- `user_daily_quests`
- `xp_transactions`
- `advancement_histories`
- `ranking_snapshots`
- `ranking_snapshot_entries`

관계:

```text
game_characters 1 --- N user_characters
user_characters 1 --- N xp_transactions
user_characters 1 --- N advancement_histories
quest_templates 1 --- N user_daily_quests
ranking_snapshots 1 --- N ranking_snapshot_entries
user_characters 1 --- N ranking_snapshot_entries
```

주요 제약:

| 테이블 | 제약 |
| --- | --- |
| `user_characters` | level 1~30, advancement BASE/FIRST/SECOND/THIRD |
| `user_characters` | 사용자당 대표 캐릭터 1개 partial unique |
| `user_characters` | nickname trim, 1~30자 |
| `level_policies` | level 1~30, min_total_xp unique |
| `quest_templates` | code unique, type ROUTINE/LLM |
| `user_daily_quests` | `(user_id, quest_date, code)` unique |
| `user_daily_quests` | status IN_PROGRESS/COMPLETED/CLAIMED/EXPIRED |
| `xp_transactions` | `(source_type, source_id)` unique |
| `advancement_histories` | `(user_character_id, stage)` unique |
| `ranking_snapshots` | `(cohort_id, period, base_date)` unique |
| `ranking_snapshot_entries` | `(snapshot_id, user_character_id)` unique |

## 15. 정합성/동시성 정책

XP 중복:

- 원장 unique 제약으로 같은 source의 중복 지급을 막는다.
- 보상 수령 시 퀘스트 row lock을 먼저 잡는다.
- EXP 지급 시 대표 캐릭터 row lock을 잡는다.

퀘스트 중복:

- 생성 전 사용자/날짜 퀘스트 존재 여부를 확인한다.
- DB에서 `(user_id, quest_date, code)` unique로 최종 방어한다.

대표 캐릭터:

- 사용자당 대표 캐릭터는 partial unique index로 하나만 허용한다.
- 보상은 수령 시점의 대표 캐릭터에 지급한다.
- 랭킹도 snapshot 생성 시점의 대표 캐릭터를 사용한다.

전직 이력:

- 레벨 재계산 후 stage를 계산한다.
- 새 stage에 도달하면 이력을 생성한다.
- `(user_character_id, stage)` unique로 같은 전직 이력 중복을 막는다.

랭킹 snapshot:

- `(cohort_id, period, base_date)` unique로 같은 기간 snapshot 중복을 막는다.
- 생성은 `INSERT ... ON CONFLICT DO NOTHING` 방식으로 처리한다.
- 동시에 같은 snapshot을 요청해도 하나만 생성되고, 나머지는 기존 snapshot을 조회한다.

## 16. API 응답 예시

### GET /gamification/home

```json
{
  "growth": {
    "userCharacterId": 10,
    "nickname": "춘식이",
    "displayName": "춘식이",
    "totalXp": 9100,
    "level": 10,
    "currentLevelXp": 1000,
    "nextLevelRequiredXp": 4000,
    "advancementStage": "FIRST"
  },
  "dailyQuests": [
    {
      "id": 101,
      "questDate": "2026-08-05",
      "type": "ROUTINE",
      "code": "ATTENDANCE",
      "title": "출석하기",
      "targetCount": 1,
      "progressCount": 1,
      "rewardXp": 20,
      "status": "COMPLETED"
    }
  ]
}
```

### GET /gamification/quests/daily

```json
[
  {
    "id": 101,
    "questDate": "2026-08-05",
    "type": "ROUTINE",
    "code": "ATTENDANCE",
    "title": "출석하기",
    "targetCount": 1,
    "progressCount": 1,
    "rewardXp": 20,
    "status": "COMPLETED"
  },
  {
    "id": 105,
    "questDate": "2026-08-05",
    "type": "LLM",
    "code": "LLM_QUEST",
    "title": "AI 추천 퀘스트",
    "targetCount": 1,
    "progressCount": 0,
    "rewardXp": 40,
    "status": "IN_PROGRESS"
  }
]
```

### POST /gamification/quests/{userDailyQuestId}/claim

```json
{
  "id": 101,
  "questDate": "2026-08-05",
  "type": "ROUTINE",
  "code": "ATTENDANCE",
  "title": "출석하기",
  "targetCount": 1,
  "progressCount": 1,
  "rewardXp": 20,
  "status": "CLAIMED"
}
```

### GET /gamification/progression?cohortId=1&aggregationDate=2026-08-05

```json
{
  "aggregationDate": "2026-08-05",
  "studySeconds": 22800,
  "reachedFourHours": true,
  "reachedSixHours": true,
  "reachedEightHours": false,
  "currentWeekdayStreakDays": 3,
  "streakQualified": true
}
```

### POST /gamification/events/*

이벤트 endpoint:

- `POST /gamification/events/attendance`
- `POST /gamification/events/study-completed`
- `POST /gamification/events/character-checked`
- `POST /gamification/events/llm-quest-completed`

응답은 `DailyQuestResponse`와 동일하다.

### GET /rankings/study?cohortId=1&period=DAILY&baseDate=2026-08-05

```json
{
  "period": "DAILY",
  "baseDate": "2026-08-05",
  "rangeStartDate": "2026-08-05",
  "rangeEndDate": "2026-08-05",
  "generatedAt": "2026-08-05T08:00:00Z",
  "top10": [
    {
      "rank": 1,
      "userId": "00000000-0000-0000-0000-000000000001",
      "userCharacterId": 10,
      "displayName": "춘식이",
      "studySeconds": 28800
    },
    {
      "rank": 1,
      "userId": "00000000-0000-0000-0000-000000000002",
      "userCharacterId": 11,
      "displayName": "감자",
      "studySeconds": 28800
    }
  ],
  "myRank": {
    "rank": 12,
    "userId": "00000000-0000-0000-0000-000000000099",
    "userCharacterId": 99,
    "displayName": "내캐릭터",
    "studySeconds": 14400
  }
}
```

## 17. 테스트 전략

단위 테스트:

- 레벨 경계값
- Lv10, Lv20, Lv30 전직
- 일일 퀘스트 중복 생성 방지
- 보상 중복 수령 방지
- 날짜 지난 보상 수령 불가
- LLM 완료 이벤트
- 대표 캐릭터 EXP 지급
- nickname 필수값
- trim 후 빈 값 거부
- 30자 초과 거부
- nickname 정상 저장
- 홈 응답 nickname 표시
- 평일 3일 스트릭
- 주말 제외 스트릭
- 결석 단절
- 4h, 6h, 8h progression
- 랭킹 기간 DAILY/WEEKLY/MONTHLY
- 공동 순위
- top10 + myRank + generatedAt
- 대표 캐릭터만 랭킹 entry 저장
- 기존 snapshot 재사용

통합 테스트 후보:

- migration 적용 후 테이블 제약 확인
- `user_daily_quests` unique 충돌 방지
- `xp_transactions` unique 충돌 방지
- `ranking_snapshots` 동시 생성
- `ranking_snapshot_entries` unique 충돌 방지
- study read-only query가 실제 schema와 맞는지 확인

테스트 실행 컨벤션:

- 단위 테스트: `*Test.java`, `mvn test`
- 통합 테스트: `*IT.java`, `mvn verify`
- Testcontainers나 실제 DB가 필요한 테스트는 반드시 IT로 분리한다.

## 18. 예상 Q&A

Q. CodeRabbit이나 SonarQube가 이 설계를 대체하나?  
A. 아니다. CodeRabbit은 설계 판단 보조, SonarQube는 정적 분석이다. 최종 정책 판단은 사람이 한다.

Q. 왜 Point를 안 쓰나?  
A. 이번 범위는 EXP only다. Point/order/achievement/badge/title은 정책과 DB 모두 제외한다.

Q. 왜 보상을 자동 지급하지 않나?  
A. 수동 수령이 있어야 사용자가 성장 피드백을 명확히 인지한다. 또한 보상 지급 시점을 하나로 모아 원장과 레벨/전직 갱신을 같은 트랜잭션으로 묶기 쉽다.

Q. 날짜 지난 퀘스트 보상은 왜 막나?  
A. 일일 퀘스트는 당일 행동 유도 장치다. 지난 보상까지 누적 수령 가능하면 일일 접속/완료 피드백이 약해진다.

Q. 왜 user가 아니라 user_character에 EXP를 저장하나?  
A. 성장 대상이 캐릭터이기 때문이다. 추후 캐릭터 교체, 다중 캐릭터, 캐릭터별 이력 확장을 고려하면 user와 성장 상태를 분리하는 편이 맞다.

Q. 랭킹에서 왜 대표 캐릭터만 쓰나?  
A. 사용자 한 명이 여러 캐릭터로 랭킹에 중복 노출되는 것을 막기 위해서다.

Q. 랭킹을 실시간으로 계산하지 않고 snapshot을 저장하는 이유는?  
A. 같은 기간의 랭킹 응답을 안정적으로 유지하고, 조회마다 큰 집계를 반복하지 않기 위해서다. generatedAt도 snapshot 생성 시점으로 고정된다.

Q. top10은 10명인가, 10위까지인가?  
A. 현재 확정은 "상위 10명"이다. 10위 동점자를 모두 포함하려면 정책 변경이 필요하다.

Q. 스트릭은 지금 무엇을 기준으로 계산하나?  
A. 최종 정책은 attendance 확정 결과 기준이다. 현재 구현은 퀘스트 완료 상태 기준이므로, attendance 확정 결과를 read-only로 참조하도록 교체하는 작업이 남아 있다.

Q. study 모듈을 수정해야 하나?  
A. 아니다. study는 공부 시간 원천이다. gamification/ranking은 study 결과를 read-only로 참조한다.

Q. LLM 퀘스트는 지금 AI가 생성하나?  
A. 아니다. 이번 범위에서는 LLM 슬롯과 완료 이벤트만 처리한다. 상세 생성은 별도 범위다.

## 19. PR 분리 제안

PR 1. gamification core

- game character/user character
- nickname
- level policy
- XP transaction
- advancement history
- daily quest
- home/daily quest/claim/event API
- 관련 단위 테스트

PR 2. progression

- 평일 스트릭
- 4h/6h/8h timer progression
- attendance/study read-only 참조
- `/gamification/progression`
- 관련 단위 테스트와 필요한 IT

PR 3. ranking

- ranking snapshot
- ranking snapshot entry
- study time ranking read-only query
- DAILY/WEEKLY/MONTHLY
- top10 + myRank
- 공동 순위
- `/rankings/study`
- snapshot 중복 방지 테스트

PR 4. 운영/문서 보강

- CodeRabbit path instructions
- 팀 테스트 컨벤션 문서
- API 예시 문서
- migration 검증용 IT

## 20. 남은 결정 사항

확정:

- EXP only
- max level 30
- 성장 단위는 `user_character`
- nickname 우선 표시
- Lv10/20/30 자동 전직
- 일일 퀘스트 5개
- 수동 보상 수령
- 날짜 지난 보상 수령 불가
- XP 원장 중복 방지
- study는 read-only 참조
- ranking snapshot 사용

보류:

- 최종 level EXP 곡선
- stage별 이미지/배경 리소스 정책
- nickname 금칙어/이모지 정책
- 스트릭을 attendance 확정 결과로 교체하는 구현 시점
- top10을 10명으로 유지할지 10위까지로 바꿀지
- 0초 사용자의 랭킹 노출 여부
