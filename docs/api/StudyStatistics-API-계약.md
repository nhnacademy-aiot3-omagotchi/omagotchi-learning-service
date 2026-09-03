# StudyStatistics API 계약

## 문서 상태

- 상태: 검토 중
- 최종 변경일: 2026-09-02
- 범위: 기수 관리자용 확정 학습 기록 및 현재 타이머 통계 조회
- base path: `/api/v1/cohorts/{cohort-id}/study-statistics`

이 문서는 현재 `StudyStatisticsController`와 response record를 기준으로 한 외부 HTTP 계약이다.

## 공통 계약

### 인증과 권한

- 모든 요청에 `Authorization: Bearer {JWT}`가 필요하다.
- 인증 사용자는 `{cohort-id}` 기수의 활성 `MANAGER` membership이어야 한다.
- 활성 기수 소속이 없으면 기수의 존재를 숨기기 위해 `404 COHORT_NOT_FOUND`를 반환한다.
- 활성 소속은 있지만 `MANAGER`가 아니면 `403 COHORT_MANAGER_REQUIRED`를 반환한다.
- 수강생 상세 대상은 같은 기수의 활성 `STUDENT` membership이어야 한다. 다른 기수, 비활성 membership 또는 비학생 대상은 모두 `404 MEMBERSHIP_NOT_FOUND`로 처리한다.

### 시간과 집계 정책

- 날짜 형식: ISO-8601 `yyyy-MM-dd`
- 시각 형식: UTC `Instant`, 예: `2026-08-11T03:00:00Z`
- 학습 시간 단위: 정수 초
- 집계일 경계: `Asia/Seoul` 기준 오전 04:00
- 서버가 주입된 `Clock`에서 요청 기준 `Instant`를 한 번 읽는다.
- 동일 요청의 `calculatedAt`, 현재 집계일과 window 기간은 같은 기준 시각에서 계산한다.
- `zoneId`와 `dayStartsAt`은 요청이나 응답에 노출하지 않는다.

### 집계 대상

- 현재 활성 `STUDENT` membership만 인원과 기수 집계에 포함한다.
- 삭제되지 않은 확정 `study_records`만 학습 시간에 포함한다.
- 오늘 요약은 정상 실행 중인 `timer_runs`의 현재 집계일 경과 시간을 추가로 포함한다.
- 수강생 page의 학습시간·학습일·건수·마지막 학습 시각은 확정 `study_records`만 사용하고, 대표 캐릭터의 `nickname`과 현재 타이머의 `isRunning`, `timerStartedAt`을 별도로 제공한다.
- 기간 추이, 수강생 overview와 일별 records는 실행 중 타이머 시간을 포함하지 않는다.
- 이름, 이메일, 랭킹과 팀 통계는 포함하지 않는다.

정상 실행 중 타이머는 `calculatedAt`보다 미래에 시작하지 않았고 `TimerTimePolicy`의 최대 실행 시간을 넘지 않은 열린 타이머다. 오늘 반영 시간은 다음과 같이 현재 집계일의 KST 04:
00 경계에서 자른다.

```text
effectiveStartedAt = max(timerStartedAt, aggregationDate의 KST 04:00)
runningSeconds = calculatedAt - effectiveStartedAt
```

### 빈 값과 평균

- 합계, 평균, 인원과 건수는 대상 값이 없으면 `0`이다.
- nullable 시각 값은 대상 기록이 없으면 DTO에서 `null`이며 현재 HTTP null 직렬화 정책에 따라 필드가 생략된다.
- 기간 추이는 요청 기간의 모든 날짜를 반환하며 기록이 없는 날짜도 `0`초로 포함한다.
- 평균 나눗셈의 나머지는 버린다.

## API 목록

| 목적             | Method | Path                                                                                                           |
|------------------|--------|----------------------------------------------------------------------------------------------------------------|
| 오늘 요약        | GET    | `/api/v1/cohorts/{cohort-id}/study-statistics/today`                                                           |
| 기간 추이        | GET    | `/api/v1/cohorts/{cohort-id}/study-statistics/trend?window={N}d`                                               |
| 수강생 통계 page | GET    | `/api/v1/cohorts/{cohort-id}/study-statistics/members?window={N}d&page=0&size=20&sort=periodStudySeconds,desc` |
| 수강생 기간 상세 | GET    | `/api/v1/cohorts/{cohort-id}/study-statistics/members/{cohort-membership-id}/overview?window={N}d`             |
| 수강생 일별 기록 | GET    | `/api/v1/cohorts/{cohort-id}/study-statistics/members/{cohort-membership-id}/records?date=yyyy-MM-dd`          |

## 1. 오늘 학습 통계

```http
GET /api/v1/cohorts/10/study-statistics/today
Authorization: Bearer {JWT}
```

성공 응답: `200 OK`

```json
{
  "aggregationDate": "2026-08-11",
  "calculatedAt": "2026-08-11T03:00:00Z",
  "totalStudySeconds": 16200,
  "activeStudentCount": 4,
  "participantCount": 3,
  "noRecordStudentCount": 1,
  "runningTimerCount": 1,
  "averageParticipantStudySeconds": 5400,
  "durationBuckets": [
    {
      "code": "NO_RECORD",
      "memberCount": 1
    },
    {
      "code": "UNDER_ONE_HOUR",
      "memberCount": 1
    },
    {
      "code": "ONE_TO_TWO_HOURS",
      "memberCount": 1
    },
    {
      "code": "TWO_TO_FOUR_HOURS",
      "memberCount": 1
    },
    {
      "code": "FOUR_HOURS_OR_MORE",
      "memberCount": 0
    }
  ]
}
```

### 필드 계산

| 필드                             | 타입    | 계산                                                                                                   |
|----------------------------------|---------|--------------------------------------------------------------------------------------------------------|
| `aggregationDate`                | date    | KST 04:00 기준 현재 집계일                                                                             |
| `calculatedAt`                   | instant | 요청에서 한 번 캡처한 서버 시각                                                                        |
| `totalStudySeconds`              | long    | 오늘 확정 record와 정상 실행 중 타이머의 현재 집계일 반영 시간 합계                                    |
| `activeStudentCount`             | long    | 기수의 활성 STUDENT 수                                                                                 |
| `participantCount`               | long    | 확정 record와 현재 타이머를 합친 오늘 합계가 0보다 큰 수강생 수                                        |
| `noRecordStudentCount`           | long    | `activeStudentCount - participantCount`                                                                |
| `runningTimerCount`              | long    | `calculatedAt` 기준 정상 실행 중인 타이머 수. 반영 시간이 0초인 막 시작한 타이머도 포함                |
| `averageParticipantStudySeconds` | long    | `totalStudySeconds / participantCount`, 참여자 0명이면 0                                               |
| `durationBuckets`                | array   | 확정 record와 현재 타이머를 합친 수강생별 오늘 시간을 아래 5개 구간으로 분류하여 고정 순서로 모두 반환 |

### 시간 구간

| code                 | 오늘 누적 학습 시간 |
|----------------------|---------------------|
| `NO_RECORD`          | 0초                 |
| `UNDER_ONE_HOUR`     | 1~3,599초           |
| `ONE_TO_TWO_HOURS`   | 3,600~7,199초       |
| `TWO_TO_FOUR_HOURS`  | 7,200~14,399초      |
| `FOUR_HOURS_OR_MORE` | 14,400초 이상       |

`durationBuckets`의 `memberCount` 합계는 `activeStudentCount`와 같다.

## 2. 기간 학습 추이

```http
GET /api/v1/cohorts/10/study-statistics/trend?window=7d
Authorization: Bearer {JWT}
```

### 요청값

| 이름     | 필수 | 형식   | 제약                                      |
|----------|------|--------|-------------------------------------------|
| `window` | 예   | `{N}d` | N은 7~60의 정수, 선행 0과 대문자 `D` 불가 |

`from = currentAggregationDate - (N - 1)`이고 `to = currentAggregationDate`다. `from`, `to`를 직접 요청할 수 없다.

성공 응답: `200 OK`

```json
{
  "window": "7d",
  "from": "2026-08-05",
  "to": "2026-08-11",
  "calculatedAt": "2026-08-11T03:00:00Z",
  "totalStudySeconds": 10800,
  "averageDailyStudySeconds": 1542,
  "dailyTotals": [
    {
      "aggregationDate": "2026-08-05",
      "studySeconds": 3600
    },
    {
      "aggregationDate": "2026-08-06",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-07",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-08",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-09",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-10",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-11",
      "studySeconds": 7200
    }
  ]
}
```

- `dailyTotals`는 `from`부터 `to`까지 날짜 오름차순이다.
- `totalStudySeconds`는 모든 `dailyTotals.studySeconds`의 합계다.
- `averageDailyStudySeconds = totalStudySeconds / N`이다. 실제 학습일 수가 분모가 아니다.

## 3. 수강생 통계 page

```http
GET /api/v1/cohorts/10/study-statistics/members?window=30d&page=0&size=20&sort=periodStudySeconds,desc
Authorization: Bearer {JWT}
```

### 요청값

| 이름     | 필수   | 기본값                    | 제약                   |
|----------|--------|---------------------------|------------------------|
| `window` | 예     | 없음                      | `{N}d`, N=7~60         |
| `page`   | 아니요 | `0`                       | 0 기반, 음수 불가      |
| `size`   | 아니요 | `20`                      | 1~100                  |
| `sort`   | 아니요 | `periodStudySeconds,desc` | 단일 `field,direction` |

허용 sort field:

- `periodStudySeconds`
- `todayStudySeconds`
- `activeStudyDays`
- `recordCount`
- `lastStudiedAt`
- `cohortMembershipId`

direction은 `asc` 또는 `desc`만 허용한다. null은 항상 마지막이며 모든 정렬에 `cohortMembershipId ASC`를 안정 정렬 조건으로 추가한다.

성공 응답: `200 OK`

```json
{
  "window": "30d",
  "from": "2026-07-13",
  "to": "2026-08-11",
  "calculatedAt": "2026-08-11T03:00:00Z",
  "items": [
    {
      "cohortMembershipId": 101,
      "userId": "00000000-0000-0000-0000-000000000101",
      "nickname": "오마",
      "todayStudySeconds": 7200,
      "periodStudySeconds": 54000,
      "activeStudyDays": 12,
      "recordCount": 15,
      "lastStudiedAt": "2026-08-11T02:00:00Z",
      "isRunning": true,
      "timerStartedAt": "2026-08-11T02:30:00Z"
    },
    {
      "cohortMembershipId": 102,
      "userId": "00000000-0000-0000-0000-000000000102",
      "todayStudySeconds": 0,
      "periodStudySeconds": 0,
      "activeStudyDays": 0,
      "recordCount": 0,
      "isRunning": false
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

- `todayStudySeconds`는 응답의 `to` 집계일에 확정된 record 값이며 실행 중 타이머 시간은 더하지 않는다.
- 나머지 학습 통계 필드도 `from..to` 안의 확정 record만으로 계산한다.
- `nickname`은 사용자의 대표 캐릭터 닉네임이며 대표 캐릭터가 없으면 값은 `null`이고 HTTP 필드는 생략된다.
- `isRunning`은 `calculatedAt` 기준 정상 실행 중 타이머 존재 여부다.
- `timerStartedAt`은 실행 중 타이머의 원래 시작 시각이며 `isRunning: false`이면 값은 `null`이고 현재 null 직렬화 정책에 따라 HTTP 필드는 생략된다.
- 타이머 상태는 page의 각 item을 가져온 뒤 배치 조회로 보강하므로 기존 확정 통계 기반 정렬과 offset/limit 결과를 변경하지 않는다.
- 기록이 없는 활성 수강생도 0/null 값으로 포함한다.
- `totalElements`는 현재 기수의 전체 활성 STUDENT 수다.
- `totalPages = ceil(totalElements / size)`이며 대상이 없으면 0이다.
- 마지막 page를 넘은 유효 요청은 `200 OK`, 빈 `items`와 동일한 전체 metadata를 반환한다.
- 전체 목록을 한 번에 반환하지 않고 DB에서 offset/limit을 적용한다.

## 4. 수강생 기간 overview

```http
GET /api/v1/cohorts/10/study-statistics/members/101/overview?window=7d
Authorization: Bearer {JWT}
```

### 요청값

| 이름                   | 위치  | 필수 | 제약                                   |
|------------------------|-------|------|----------------------------------------|
| `cohort-membership-id` | path  | 예   | 같은 기수의 활성 STUDENT membership ID |
| `window`               | query | 예   | `{N}d`, N=7~60                         |

성공 응답: `200 OK`

```json
{
  "cohortMembershipId": 101,
  "userId": "00000000-0000-0000-0000-000000000101",
  "window": "7d",
  "from": "2026-08-05",
  "to": "2026-08-11",
  "calculatedAt": "2026-08-11T03:00:00Z",
  "totalStudySeconds": 10800,
  "averageDailyStudySeconds": 1542,
  "activeStudyDays": 2,
  "recordCount": 2,
  "lastStudiedAt": "2026-08-11T02:00:00Z",
  "dailyTotals": [
    {
      "aggregationDate": "2026-08-05",
      "studySeconds": 3600
    },
    {
      "aggregationDate": "2026-08-06",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-07",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-08",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-09",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-10",
      "studySeconds": 0
    },
    {
      "aggregationDate": "2026-08-11",
      "studySeconds": 7200
    }
  ]
}
```

- `averageDailyStudySeconds = totalStudySeconds / N`이다.
- `activeStudyDays`는 기간 안에 확정 record가 있는 서로 다른 집계일 수다.
- `recordCount`와 `lastStudiedAt`도 요청 기간 안에서 계산한다.
- 원본 `records`는 이 API에 포함하지 않는다.

## 5. 수강생 선택 집계일 기록

```http
GET /api/v1/cohorts/10/study-statistics/members/101/records?date=2026-08-11
Authorization: Bearer {JWT}
```

### 요청값

| 이름                   | 위치  | 필수 | 제약                                                |
|------------------------|-------|------|-----------------------------------------------------|
| `cohort-membership-id` | path  | 예   | 같은 기수의 활성 STUDENT membership ID              |
| `date`                 | query | 예   | 집계일 `yyyy-MM-dd`, 현재 집계일보다 미래일 수 없음 |

과거 날짜에는 별도의 60일 하한을 적용하지 않는다.

성공 응답: `200 OK`

```json
{
  "cohortMembershipId": 101,
  "userId": "00000000-0000-0000-0000-000000000101",
  "date": "2026-08-11",
  "calculatedAt": "2026-08-11T03:00:00Z",
  "totalStudySeconds": 5400,
  "records": [
    {
      "id": "10000000-0000-0000-0000-000000000001",
      "startTime": "2026-08-10T23:00:00Z",
      "endTime": "2026-08-11T00:30:00Z",
      "studySeconds": 5400
    }
  ]
}
```

- `records`는 `startTime ASC, id ASC`로 정렬한다.
- `totalStudySeconds`는 반환된 `records.studySeconds`의 합계다.
- 기록이 없는 유효 대상과 날짜는 `200 OK`, `totalStudySeconds: 0`, `records: []`다.
- `version`, `createdAt`, `updatedAt`, `deletedAt`은 반환하지 않는다.

## 오류 계약

공통 오류 body:

```json
{
  "status": 400,
  "code": "COMMON_INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/cohorts/10/study-statistics/trend",
  "requestId": null
}
```

현재 `requestId`는 nullable이며 Statistics API가 별도로 생성하지 않는다.

| 조건                                 | HTTP | code                           |
|--------------------------------------|------|--------------------------------|
| JWT 없음 또는 인증 실패              | 401  | `AUTH_AUTHENTICATION_REQUIRED` |
| 활성 기수 소속 없음                  | 404  | `COHORT_NOT_FOUND`             |
| 활성 소속이지만 MANAGER가 아님       | 403  | `COHORT_MANAGER_REQUIRED`      |
| 다른 기수·비활성·비학생 상세 대상    | 404  | `MEMBERSHIP_NOT_FOUND`         |
| window 누락·형식·범위 오류           | 400  | `COMMON_INVALID_REQUEST`       |
| page 음수, size 범위 오류, sort 오류 | 400  | `COMMON_INVALID_REQUEST`       |
| date 누락·형식 오류·미래 집계일      | 400  | `COMMON_INVALID_REQUEST`       |

## 캐시와 호출 분리 메모

이 절은 frontend 구현 요구가 아니라 연동 시 사용할 수 있는 권장사항이다.

- Today, Trend, Members, Overview와 Records는 서로 독립적으로 호출하고 실패·갱신 범위를 분리한다.
- `calculatedAt`은 데이터 freshness 표시에 사용할 수 있지만 서로 다른 요청이 동일 DB snapshot을 보장한다는 의미는 아니다.
- members page cache key 후보는 `cohortId + window + page + size + sort`다.
- overview cache key 후보는 `cohortId + cohortMembershipId + window`다.
- records cache key 후보는 `cohortId + cohortMembershipId + date`다.
- 현재 서버는 ETag와 명시적인 `Cache-Control` 정책을 제공하지 않는다.
- frontend 코드, cache store와 invalidation 구현은 이번 backend 범위에서 변경하지 않았다.

## 보류 및 별도 기능

- frontend에서 `isRunning`, `timerStartedAt`, `calculatedAt`을 이용한 1초 단위 화면 증가와 서버 재동기화
- 기간 추이, 수강생 overview와 일별 records의 실행 중 TimerRun 경과 시간 합산
- 개인/기수/팀 랭킹
- 팀별 합산 통계
- 이름·이메일 결합과 이름 검색
- 임의 `from`, `to` 범위 조회
- rollup table 또는 materialized view

위 항목은 현재 응답에 암묵적으로 추가하지 않고 별도 계약과 task로 다룬다.
