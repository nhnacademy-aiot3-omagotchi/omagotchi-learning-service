# StudyRanking API 계약

## 문서 상태

- 상태: 구현 기준, 검토 중
- 최종 변경일: 2026-08-20
- 범위: 기수 수강생용 오늘 및 확정 기간 학습 랭킹 조회
- 수강생 base path: `/api/v1/cohorts/{cohortId}/study-rankings`

이 문서는 `MemberStudyRankingController`와 response record가 구현할 수강생용 외부 HTTP 계약이다.
관리자 화면의 학습시간 상위 목록은 별도 Ranking API가 아니라 `StudyStatistics-API-계약.md`의 수강생 통계 page를 정렬해 조회한다.

## 공통 계약

### 인증과 권한

- 모든 요청에 `Authorization: Bearer {JWT}`가 필요하다.
- 수강생 랭킹 보드와 내 랭킹은 인증 사용자가 `{cohortId}` 기수의 활성 `STUDENT` membership이어야 한다.
- 수강생 여부는 JWT의 전역 role이 아니라 기수 membership으로 판정한다.
- 활성 기수 소속이 없으면 기수의 존재를 숨기기 위해 `404 COHORT_NOT_FOUND`를 반환한다.
- 활성 소속은 있지만 수강생 API의 `STUDENT`가 아니면 `403 COHORT_ACCESS_DENIED`를 반환한다.

### 요청 기준 시각과 집계일

- 서버가 주입된 `Clock`에서 요청 기준 시각 `calculatedAt`을 한 번만 읽는다.
- 집계일 경계는 `Asia/Seoul` 기준 오전 04:00이다.
- KST `2026-08-20 03:59`의 현재 집계일은 `2026-08-19`이고, `04:00`부터 `2026-08-20`이다.
- `zoneId`와 `dayStartsAt`은 요청이나 응답에 노출하지 않는다.

### 집계 후보와 순위

- 요청 기수의 `ACTIVE`·`STUDENT`이며 `endedAt`이 없는 membership만 후보가 된다.
- 해당 조회 방식으로 계산된 학습시간 합계가 `0`인 수강생은 랭킹에서 제외하고 `ranked: false`로 처리한다.
- `rankedMemberCount`는 전체 활성 수강생 수가 아니라 계산된 학습시간이 `0`보다 큰 랭킹 참여자 수다.
- 학습 시간 단위는 정수 초다.
- 합계 학습 시간이 큰 수강생부터 정렬한다.
- 동점은 같은 순위를 사용하고 다음 순위를 건너뛰는 competition ranking이다.
- 예: `7200, 3600, 3600, 1800초`는 `1, 2, 2, 4위`다.
- 동점 안에서는 내부 `cohortMembershipId ASC`로 안정 정렬하지만 식별자는 응답하지 않는다.
- `maxRank`는 반환 개수가 아니라 포함할 최대 순위다.
- `maxRank=2`일 때 `1, 2, 2위`가 존재하면 3명을 반환한다.
- 모든 인원이 같은 `1위`라면 `maxRank=1`이어도 모든 동점자를 반환할 수 있다.

### 공통 요청값

| 이름 | 위치 | 필수 | 기본값 | 제약 |
| --- | --- | --- | --- | --- |
| `cohortId` | path | 예 | 없음 | `Long` 형식의 기수 ID |
| `maxRank` | query | 보드 API만 선택 | `100` | 1~1,000의 정수 |

- `maxRank`는 수강생 보드에서만 사용한다.

## 기간별 API

### 수강생 보드와 내 순위

| 구분 | Method | Path |
| --- | --- | --- |
| 오늘 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/today?maxRank={N}` |
| 일간 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/daily/{date}?maxRank={N}` |
| 주간 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/weekly/{weekStartDate}?maxRank={N}` |
| 월간 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/monthly/{month}?maxRank={N}` |

보드의 순위 상한과 관계없이 인증 사용자의 순위를 `myRanking`에 별도로 반환한다.

## 1. 오늘 랭킹

### 집계 범위

- 오늘 API는 현재 집계일의 삭제되지 않은 확정 `study_records`를 합산한다.
- 정상 실행 중인 `timer_runs`의 현재 집계일 경과 시간을 추가로 합산한다.
- 정상 실행 여부는 단순히 `ended_at IS NULL`로 판정하지 않고 `TimerTimePolicy`의 만료 규칙까지 적용한다.
- 만료·종료·폐기된 타이머는 합산하지 않는다.

정상 실행 중인 타이머의 오늘 반영 구간은 다음과 같다.

```text
aggregationStartedAt = 현재 집계일의 KST 04:00
effectiveStartedAt = max(timerRun.startedAt, aggregationStartedAt)
runningSeconds = secondsBetween(effectiveStartedAt, calculatedAt)
```

예를 들어 KST `03:00`에 시작한 타이머를 `05:30`에 조회하면:

- 개인 타이머의 전체 경과 시간은 2시간 30분이다.
- 오늘 랭킹에는 `04:00~05:30` 1시간 30분만 반영한다.
- 04:00에 `timer_runs`를 종료하거나 새 타이머를 생성하지 않는다.
- 전날 구간은 타이머가 정상 종료되어 `study_records`가 생성된 후 전날 확정 랭킹에 반영한다.

### 수강생 보드 예시

```http
GET /api/v1/cohorts/10/study-rankings/today?maxRank=2
Authorization: Bearer {JWT}
```

```json
{
  "aggregationDate": "2026-08-20",
  "calculatedAt": "2026-08-20T05:30:00Z",
  "rankedMemberCount": 4,
  "returnedEntryCount": 3,
  "entries": [
    {
      "rank": 1,
      "displayName": "첫째",
      "studySeconds": 7200,
      "timerRunning": true
    },
    {
      "rank": 2,
      "displayName": "둘째",
      "studySeconds": 3600,
      "timerRunning": false
    },
    {
      "rank": 2,
      "displayName": "셋째",
      "studySeconds": 3600,
      "timerRunning": true
    }
  ],
  "myRanking": {
    "ranked": true,
    "ranking": {
      "rank": 4,
      "displayName": "나",
      "studySeconds": 1800,
      "timerRunning": false
    }
  }
}
```

- `aggregationDate`는 응답이 대상으로 삼은 현재 집계일이다.
- `calculatedAt`은 확정 기록과 실행 중 시간을 합산한 기준 시각이다.
- `timerRunning`은 오늘 API에서만 반환하며 `studySeconds`에 정상 실행 중 타이머가 포함되었는지를 나타낸다.

## 2. 일간 랭킹

```http
GET /api/v1/cohorts/10/study-rankings/daily/2026-08-19?maxRank=2
Authorization: Bearer {JWT}
```

- `date`는 `yyyy-MM-dd` 형식이다.
- `date`는 현재 집계일보다 과거여야 한다.
- 현재 집계일과 미래는 `400 COMMON_INVALID_REQUEST`다.
- `startDate = date`, `includedThroughDate = date`다.
- 해당 일자의 삭제되지 않은 확정 `study_records`만 합산한다.

## 3. 주간 랭킹

```http
GET /api/v1/cohorts/10/study-rankings/weekly/2026-08-17?maxRank=2
Authorization: Bearer {JWT}
```

- `weekStartDate`는 `yyyy-MM-dd` 형식이며 반드시 월요일이어야 한다.
- 주간은 `weekStartDate`부터 일요일까지의 달력 주다.
- 미래 주의 월요일은 `400 COMMON_INVALID_REQUEST`다.
- 과거 주는 월요일~일요일 전체를 집계한다.
- 현재 주는 월요일부터 현재 집계일의 전날까지 집계한다.
- 현재 주 월요일에는 확정된 집계일이 없으므로 `includedThroughDate: null`과 빈 랭킹을 반환한다.

현재 집계일이 `2026-08-20` 목요일일 때:

| `weekStartDate` | `startDate` | `includedThroughDate` | 결과 |
| --- | --- | --- | --- |
| `2026-08-10` | `2026-08-10` | `2026-08-16` | 과거 전체 주 |
| `2026-08-17` | `2026-08-17` | `2026-08-19` | 현재 주 부분 기간 |
| `2026-08-18` | - | - | 월요일이 아니므로 400 |
| `2026-08-24` | - | - | 미래 주이므로 400 |

## 4. 월간 랭킹

```http
GET /api/v1/cohorts/10/study-rankings/monthly/2026-08?maxRank=2
Authorization: Bearer {JWT}
```

- `month`는 `yyyy-MM` 형식이다.
- 현재 집계월보다 미래인 월은 `400 COMMON_INVALID_REQUEST`다.
- 과거 월은 1일부터 말일까지 집계한다.
- 현재 월은 1일부터 현재 집계일의 전날까지 집계한다.
- 현재 월 1일에는 확정된 집계일이 없으므로 `includedThroughDate: null`과 빈 랭킹을 반환한다.

현재 집계일이 `2026-08-20`일 때:

| `month` | `startDate` | `includedThroughDate` |
| --- | --- | --- |
| `2026-08` | `2026-08-01` | `2026-08-19` |
| `2026-07` | `2026-07-01` | `2026-07-31` |

## 확정 기간 응답

일간·주간·월간 API는 같은 응답 구조를 사용하며 `timerRunning`을 반환하지 않는다.

```json
{
  "startDate": "2026-08-17",
  "includedThroughDate": "2026-08-19",
  "rankedMemberCount": 2,
  "returnedEntryCount": 2,
  "entries": [
    {
      "rank": 1,
      "displayName": "첫째",
      "studySeconds": 14400
    },
    {
      "rank": 2,
      "displayName": "둘째",
      "studySeconds": 10800
    }
  ]
}
```

- `startDate`는 요청이 선택한 기간의 시작 집계일이다.
- `includedThroughDate`는 실제로 합산된 마지막 집계일이다.
- `includedThroughDate: null`은 유효한 요청이지만 아직 포함할 확정 집계일이 없음을 뜻한다.
- `includedThroughDate`가 있지만 `rankedMemberCount: 0`이면 해당 확정 기간의 학습 기록이 없음을 뜻한다.

## 응답 필드

### 보드

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `rankedMemberCount` | long | 아니요 | 계산된 학습시간이 0보다 큰 전체 랭킹 참여자 수 |
| `returnedEntryCount` | int | 아니요 | 실제 반환된 `entries` 수 |
| `entries` | array | 아니요 | `rank <= maxRank`인 랭킹 항목 |

### 랭킹 항목

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `rank` | long | 아니요 | 공동 순위가 반영된 순위 |
| `displayName` | string | 예 | 대표 캐릭터의 표시명. 대표 캐릭터가 없으면 현재 구현상 `null` 가능 |
| `studySeconds` | long | 아니요 | 해당 조회 방식으로 계산된 학습시간 합계(초) |
| `timerRunning` | boolean | 아니요 | 오늘 API 전용. `studySeconds`에 정상 실행 중 타이머가 포함되었는지 여부 |

- 확정 기간 API는 `timerRunning` 필드 자체를 응답하지 않는다.
- `userId`와 `cohortMembershipId`는 외부 응답에 노출하지 않는다.
- 이름·이메일은 제공하지 않으며 `displayName`은 사용자 프로필 이름이 아니라 대표 캐릭터 표시명이다.

### 개인 순위

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `ranked` | boolean | 아니요 | 요청자가 랭킹 참여자인지 여부 |
| `ranking` | object | 예 | `ranked: true`이면 랭킹 항목, 아니면 `null` |

- `myRanking`은 보드의 순위 상한과 독립적으로 계산한다.
- 보드 최상위의 `rankedMemberCount`와 중복되므로 `myRanking` 안에는 포함하지 않는다.
- 오늘 보드는 최상위에 `aggregationDate`, `calculatedAt`을 포함한다.
- 확정 기간 보드는 최상위에 `startDate`, nullable `includedThroughDate`를 포함한다.

## 빈 랭킹

확정 집계일은 있지만 참여자가 없으면:

```json
{
  "startDate": "2026-08-17",
  "includedThroughDate": "2026-08-19",
  "rankedMemberCount": 0,
  "returnedEntryCount": 0,
  "entries": [],
  "myRanking": {
    "ranked": false,
    "ranking": null
  }
}
```

현재 주 월요일이나 현재 월 1일처럼 포함할 확정 집계일이 없으면:

```json
{
  "startDate": "2026-08-17",
  "includedThroughDate": null,
  "rankedMemberCount": 0,
  "returnedEntryCount": 0,
  "entries": [],
  "myRanking": {
    "ranked": false,
    "ranking": null
  }
}
```

## 오류 계약

공통 오류 body:

```json
{
  "code": "COMMON_INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/cohorts/10/study-rankings/daily/not-a-date",
  "requestId": null
}
```

- HTTP 상태는 response status로 전달하며 body에 `status` 필드를 중복하지 않는다.
- `path`에는 query string을 포함하지 않는다.
- 현재 `requestId`는 nullable이며 Ranking API가 별도로 생성하지 않는다.

| 조건 | HTTP | code |
| --- | --- | --- |
| JWT 없음 또는 인증 실패 | 401 | `AUTH_AUTHENTICATION_REQUIRED` |
| `date`, `weekStartDate` 형식 오류 | 400 | `COMMON_INVALID_REQUEST` |
| `month` 형식 오류 | 400 | `COMMON_INVALID_REQUEST` |
| 일간 `date`가 현재 집계일 이상 | 400 | `COMMON_INVALID_REQUEST` |
| `weekStartDate`가 월요일이 아님 | 400 | `COMMON_INVALID_REQUEST` |
| `weekStartDate`가 미래 주 | 400 | `COMMON_INVALID_REQUEST` |
| `month`가 현재 집계월보다 미래 | 400 | `COMMON_INVALID_REQUEST` |
| `maxRank < 1` 또는 `maxRank > 1000` | 400 | `COMMON_INVALID_REQUEST` |
| `cohortId` 또는 `maxRank` 숫자 변환 실패 | 400 | `COMMON_INVALID_REQUEST` |
| 활성 기수 소속 없음 | 404 | `COHORT_NOT_FOUND` |
| 수강생 API를 STUDENT가 아닌 활성 소속이 호출 | 403 | `COHORT_ACCESS_DENIED` |

## 호출과 갱신 특성

- 별도 ranking table이나 ranking snapshot을 사용하지 않는다.
- 일간·주간·월간 API는 요청할 때마다 현재 확정 `study_records`를 직접 기간 합산한다.
- 오늘 API는 확정 `study_records`와 정상 실행 중인 `timer_runs`를 `calculatedAt` 기준으로 합산한다.
- record 생성·수정·삭제, 타이머 종료·폐기 후 다음 호출부터 결과가 달라질 수 있다.
- 수강생 보드의 보드와 `myRanking`은 같은 랭킹 집계 결과에서 조립한다.
- 서버는 현재 Ranking API에 ETag나 명시적인 `Cache-Control` 정책을 제공하지 않는다.

## 보류 및 별도 기능

- 날짜 하한 검증(기수 시작일 기준)
- ranking snapshot 또는 기간 마감 후 불변 랭킹
- 04:00 시점의 타이머 자동 체크포인트·중간 확정
- 출석률이나 팀 점수를 결합한 복합 랭킹
- 사용자 이름·이메일·프로필 API 결합
- 프런트 polling, SSE·WebSocket, 초 단위 표시 갱신
- Ranking API의 명시적인 HTTP 캐시 정책

위 항목은 현재 응답에 암묵적으로 추가하지 않고 별도 계약과 task로 다룬다.
