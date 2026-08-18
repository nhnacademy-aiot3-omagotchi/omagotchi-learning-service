# StudyRanking API 계약

## 문서 상태

- 상태: 구현 기준, 검토 중
- 최종 변경일: 2026-08-13
- 범위: 기수 수강생·관리자용 확정 학습 기록 랭킹 조회
- 수강생 base path: `/api/v1/cohorts/{cohortId}/study-rankings`
- 관리자 base path: `/api/v1/cohorts/{cohortId}/study-rankings/management`

이 문서는 현재 `MemberStudyRankingController`, `ManagerStudyRankingController`와 response record를 기준으로 한 외부 HTTP 계약이다.

## 공통 계약

### 인증과 권한

- 모든 요청에 `Authorization: Bearer {JWT}`가 필요하다.
- 수강생 랭킹 보드와 내 랭킹은 인증 사용자가 `{cohortId}` 기수의 활성 `STUDENT` membership이어야 한다.
- 관리자 랭킹 보드는 인증 사용자가 `{cohortId}` 기수의 활성 `MANAGER` membership이어야 한다.
- 수강생·관리자 여부는 JWT의 전역 role이 아니라 기수 membership으로 판정한다.
- 활성 기수 소속이 없으면 기수의 존재를 숨기기 위해 `404 COHORT_NOT_FOUND`를 반환한다.
- 활성 소속은 있지만 수강생 API의 `STUDENT`가 아니면 `403 COHORT_ACCESS_DENIED`를 반환한다.
- 활성 소속은 있지만 관리자 API의 `MANAGER`가 아니면 `403 COHORT_MANAGER_REQUIRED`를 반환한다.

### 시간과 기간 정책

- 서버가 주입된 `Clock`에서 요청 기준 시각을 한 번 읽는다.
- 집계일 경계는 `Asia/Seoul` 기준 오전 04:00이다.
- 모든 기간은 요청 시점의 현재 집계일을 포함한다.
- 클라이언트는 기준 날짜를 전달할 수 없으며 과거 일간·과거 주간·과거 월간 랭킹을 선택할 수 없다.

현재 집계일이 `2026-08-13`일 때의 기간은 다음과 같다.

| `period` | 시작 집계일 | 종료 집계일 | 의미 |
| --- | --- | --- | --- |
| `DAILY` | `2026-08-13` | `2026-08-13` | 현재 집계일 |
| `WEEKLY` | 해당 주 월요일 | `2026-08-13` | 월요일부터 현재 집계일까지 |
| `MONTHLY` | `2026-08-01` | `2026-08-13` | 이번 달 1일부터 현재 집계일까지 |

예를 들어 KST `2026-08-13 03:59` 요청은 오전 04:00 경계 전이므로 현재 집계일을 `2026-08-12`로 계산한다.

### 집계 대상

- 요청 기수의 `ACTIVE`·`STUDENT`이며 `endedAt`이 없는 membership만 후보가 된다.
- 선택 기간 안의 삭제되지 않은 확정 `study_records`만 합산한다.
- 학습 시간은 `study_records.study_seconds`의 합계이며 단위는 정수 초다.
- 기간 합계가 `0`인 수강생은 랭킹에서 제외되고 `ranked: false`로 처리한다.
- `rankedMemberCount`는 전체 활성 수강생 수가 아니라 기간 합계가 `0`보다 큰 랭킹 참여자 수다.
- 실행 중 `timer_runs`의 경과 시간은 현재 포함하지 않는다.

### 순위와 정렬

- 기간 합계 학습 시간이 큰 수강생부터 정렬한다.
- 동점은 같은 순위를 사용하고 다음 순위를 건너뛰는 competition ranking이다.
- 예: `7200, 3600, 3600, 1800초`는 `1, 2, 2, 4위`다.
- 동점 안에서는 내부 `cohortMembershipId ASC`로 안정 정렬하지만 식별자는 응답하지 않는다.
- `maxRank`는 반환 개수가 아니라 포함할 최대 순위다.
- 따라서 `maxRank=2`일 때 `1, 2, 2위`가 존재하면 3명을 반환한다.
- 모든 인원이 같은 `1위`라면 `maxRank=1`이어도 모든 동점자를 반환할 수 있다.
- `maxRank`가 랭킹 참여자 수보다 크면 참여자 전원을 반환한다.

### 공통 요청값

| 이름 | 위치 | 필수 | 기본값 | 제약 |
| --- | --- | --- | --- | --- |
| `cohortId` | path | 예 | 없음 | `Long` 형식의 기수 ID |
| `period` | query | 예 | 없음 | `DAILY`, `WEEKLY`, `MONTHLY` 중 하나 |
| `maxRank` | query | 보드 API만 선택 | `100` | 1~1,000의 정수 |

- `period`는 대문자 enum 값만 지원한다.
- 제거된 `TODAY`, 소문자 값, 누락된 값은 `400 COMMON_INVALID_REQUEST`다.
- `maxRank`는 수강생 보드와 관리자 보드에서만 사용하는 계약이다. `/me` 계약에는 포함되지 않는다.
- 응답에는 요청한 `period`, `maxRank`, 기준 날짜 또는 계산 시각을 반복해서 반환하지 않는다.

## API 목록

| 목적 | Method | Path | 권한 |
| --- | --- | --- | --- |
| 수강생 보드와 내 순위 | GET | `/api/v1/cohorts/{cohortId}/study-rankings?period={period}&maxRank={N}` | 활성 STUDENT |
| 내 순위만 조회 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/me?period={period}` | 활성 STUDENT |
| 관리자 보드 | GET | `/api/v1/cohorts/{cohortId}/study-rankings/management?period={period}&maxRank={N}` | 활성 MANAGER |

## 1. 수강생 보드와 내 순위

상위 랭킹 보드와 인증 사용자의 순위를 한 응답으로 반환한다. 내 순위가 `maxRank` 밖에 있어도 `myRanking`에서 별도로 반환한다.

```http
GET /api/v1/cohorts/10/study-rankings?period=DAILY&maxRank=2
Authorization: Bearer {JWT}
```

성공 응답: `200 OK`

```json
{
  "rankedMemberCount": 4,
  "returnedEntryCount": 3,
  "entries": [
    {
      "rank": 1,
      "displayName": "첫째",
      "studySeconds": 7200
    },
    {
      "rank": 2,
      "displayName": "둘째",
      "studySeconds": 3600
    },
    {
      "rank": 2,
      "displayName": "셋째",
      "studySeconds": 3600
    }
  ],
  "myRanking": {
    "ranked": true,
    "ranking": {
      "rank": 4,
      "displayName": "나",
      "studySeconds": 1800
    }
  }
}
```

- `returnedEntryCount`는 `entries.length`와 같다.
- `myRanking`은 보드의 순위 상한과 독립적이다.
- 본인이 `entries`에 포함되는 경우 동일한 랭킹 정보가 `myRanking.ranking`에도 포함된다.
- 랭킹 참여자가 없으면 `rankedMemberCount: 0`, `returnedEntryCount: 0`, `entries: []`다.

모든 수강생의 기간 합계가 0이라 본인도 미랭크인 경우:

```json
{
  "rankedMemberCount": 0,
  "returnedEntryCount": 0,
  "entries": [],
  "myRanking": {
    "ranked": false,
    "ranking": null
  }
}
```

## 2. 내 순위만 조회

랭킹 보드가 필요하지 않은 화면에서 인증 사용자의 순위만 조회한다.

```http
GET /api/v1/cohorts/10/study-rankings/me?period=MONTHLY
Authorization: Bearer {JWT}
```

랭크된 성공 응답: `200 OK`

```json
{
  "rankedMemberCount": 12,
  "ranked": true,
  "ranking": {
    "rank": 7,
    "displayName": "나",
    "studySeconds": 25200
  }
}
```

미랭크 성공 응답: `200 OK`

```json
{
  "rankedMemberCount": 11,
  "ranked": false,
  "ranking": null
}
```

- `ranked: false`는 요청자가 활성 수강생이지만 선택 기간의 확정 학습시간 합계가 0이라는 의미다.
- 다른 수강생의 보드 목록과 `returnedEntryCount`는 반환하지 않는다.

## 3. 관리자 보드

관리자가 상위 랭킹 보드만 조회한다. 호출한 관리자의 개인 순위는 계산하거나 반환하지 않는다.

```http
GET /api/v1/cohorts/10/study-rankings/management?period=WEEKLY
Authorization: Bearer {JWT}
```

`maxRank`를 생략했으므로 순위 상한은 기본값 `100`이다.

성공 응답: `200 OK`

```json
{
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

- `myRanking`은 반환하지 않는다.
- `returnedEntryCount`는 `entries.length`와 같다.

## 응답 필드

### 보드

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `rankedMemberCount` | long | 아니요 | 기간 합계가 0보다 큰 전체 랭킹 참여자 수 |
| `returnedEntryCount` | int | 아니요 | 실제 반환된 `entries` 수 |
| `entries` | array | 아니요 | `rank <= maxRank`인 랭킹 항목 |

### 랭킹 항목

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `rank` | long | 아니요 | 공동 순위가 반영된 순위 |
| `displayName` | string | 예 | 대표 캐릭터의 표시명. 대표 캐릭터가 없으면 현재 구현상 `null` 가능 |
| `studySeconds` | long | 아니요 | 요청 기간 확정 학습시간 합계(초) |

- `userId`와 `cohortMembershipId`는 외부 응답에 노출하지 않는다.
- 이름·이메일은 제공하지 않으며 `displayName`은 사용자 프로필 이름이 아니라 대표 캐릭터 표시명이다.

### 개인 순위

| 필드 | 타입 | nullable | 의미 |
| --- | --- | --- | --- |
| `ranked` | boolean | 아니요 | 요청자가 랭킹 참여자인지 여부 |
| `ranking` | object | 예 | `ranked: true`이면 랭킹 항목, 아니면 `null` |

`/me` 최상위 응답에는 전체 참여자 수를 알 수 있도록 `rankedMemberCount`를 추가한다. 수강생 보드의 `myRanking`에는 보드와 중복되므로 포함하지 않는다.

## 오류 계약

공통 오류 body:

```json
{
  "code": "COMMON_INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/cohorts/10/study-rankings",
  "requestId": null
}
```

- HTTP 상태는 response status로 전달하며 body에 `status` 필드를 중복해서 넣지 않는다.
- `path`에는 query string을 포함하지 않는다.
- 현재 `requestId`는 nullable이며 Ranking API가 별도로 생성하지 않는다.

| 조건 | HTTP | code |
| --- | --- | --- |
| JWT 없음 또는 인증 실패 | 401 | `AUTH_AUTHENTICATION_REQUIRED` |
| `period` 누락·지원하지 않는 값·소문자 값 | 400 | `COMMON_INVALID_REQUEST` |
| `maxRank < 1` 또는 `maxRank > 1000` | 400 | `COMMON_INVALID_REQUEST` |
| `cohortId` 또는 `maxRank` 숫자 변환 실패 | 400 | `COMMON_INVALID_REQUEST` |
| 활성 기수 소속 없음 | 404 | `COHORT_NOT_FOUND` |
| 수강생 API를 STUDENT가 아닌 활성 소속이 호출 | 403 | `COHORT_ACCESS_DENIED` |
| 관리자 API를 MANAGER가 아닌 활성 소속이 호출 | 403 | `COHORT_MANAGER_REQUIRED` |

## 호출과 갱신 특성

- 별도 ranking table이나 ranking snapshot을 사용하지 않는다.
- 요청마다 현재 확정 `study_records`를 기간 합산해 조회하므로 record 생성·수정·삭제 후 다음 호출부터 결과가 달라질 수 있다.
- `/study-rankings`의 보드와 내 순위는 같은 랭킹 집계 결과에서 조립한다.
- `/study-rankings`와 `/me`를 별도 호출하면 호출 사이의 record 변경으로 서로 다른 결과를 볼 수 있다.
- 서버는 현재 Ranking API에 ETag나 명시적인 `Cache-Control` 정책을 제공하지 않는다.
- 응답에 기간과 기준 시각을 포함하지 않으므로 클라이언트 cache key에는 최소한 `cohortId + period + endpoint`를 포함하고, 보드 호출에는 `maxRank`도 포함해야 한다.

## 보류 및 별도 기능

- 현재 집계일에 실행 중인 `timer_runs` 경과 시간 합산
- 과거 기준 날짜를 지정하는 일간·주간·월간 랭킹
- ranking snapshot 또는 기간 마감 후 불변 랭킹
- 출석률이나 팀 점수를 결합한 복합 랭킹
- 사용자 이름·이메일·프로필 API 결합

위 항목은 현재 응답에 암묵적으로 추가하지 않고 별도 계약과 task로 다룬다.
