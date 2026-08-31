# Study Team Ranking API 계약

## 문서 상태

- 상태: 적용 계약
- 작성일: 2026-08-20
- 최종 수정일: 2026-08-21
- 범위: 현재 팀 소속을 필터 또는 그룹 기준으로 사용하는 팀 내부 개인 랭킹과 팀 간 랭킹
- 비범위: 팀별 학습 기록 저장, 과거 팀 소속 복원, DB migration

이 문서는 팀 내부 개인 랭킹과 팀 간 랭킹의 현재 HTTP 및 Application 구현 계약이다.
인증·권한·오류 계약과 기간 계산·오늘 타이머 반영 규칙은 `StudyRanking-API-계약.md`를
함께 따른다.

## 공통 계약

### 인증과 권한

- 모든 요청에 `Authorization: Bearer {JWT}`가 필요하다.
- 수강생 여부는 JWT의 전역 role이 아니라 기수 membership으로 판정한다.
- 활성 기수 소속이 없으면 `404 COHORT_NOT_FOUND`를 반환한다.
- 활성 소속은 있지만 `STUDENT`가 아니면 `403 COHORT_ACCESS_DENIED`를 반환한다.

세부 오류 코드와 공통 오류 body 형식은 `StudyRanking-API-계약.md`의 오류 계약을 따른다.

## 핵심 결정

Team은 학습시간의 발생 장소나 귀속 대상이 아니다. Team은 랭킹을 조회할 때
현재 수강생 후보를 제한하거나 현재 팀별로 묶는 기준이다.

- 팀 내부 개인 랭킹: 선택한 팀의 현재 팀원만 남기는 후보 필터
- 팀 간 랭킹: 현재 팀 소속으로 개인 학습시간을 그룹화하는 기준
- 학습시간: 기수 membership 개인에게 계산되는 기존 학습시간
- 팀별 학습 기록: 만들지 않음
- 과거 팀 소속 이력과 팀 랭킹 snapshot: 사용하지 않음

따라서 `study_records`와 `timer_runs`에 `teamId`를 추가하지 않는다. “A팀에서
공부한 시간”과 “B팀에서 공부한 시간”도 구분하지 않는다.

## 용어

| 용어 | 의미 | 순위 주체 |
| --- | --- | --- |
| 팀 내부 개인 랭킹 | 선택한 팀의 현재 팀원만 후보로 삼아 기존 개인 학습시간을 비교 | cohort membership |
| 팀 간 랭킹 | 현재 팀원들의 개인 학습시간을 팀별로 합산하여 비교 | team |
| 현재 팀 필터 | 조회 시점의 유효한 `team_members` 관계로 후보를 제한하거나 그룹화하는 규칙 | - |
| 팀 합산 학습시간 | 현재 팀원들의 해당 조회 기간 개인 학습시간 합계 | team |

“팀별 학습시간”은 특정 팀에 귀속되어 저장된 학습시간처럼 읽힐 수 있으므로
내부 용어로 사용하지 않는다. 계약·클래스·테스트 이름은 `팀 내부 개인 랭킹`과
`팀 간 랭킹`을 구분한다.

## 현재 모델과의 정합성

- `Team`은 `cohortId`를 가지며 해체된 팀은 `deletedAt`으로 구분한다.
- `TeamMember`의 주체 키는 `userId`가 아니라 `cohortMembershipId`다.
- 하나의 `cohortMembershipId`는 하나의 팀에만 속할 수 있다.
- 팀 탈퇴·제외·해체 시 `team_members`를 물리 삭제하므로 현재 소속만 남는다.
- 현재 개인 랭킹은 요청 기수의 `ACTIVE STUDENT` membership만 후보로 사용한다.
- 오늘 학습시간은 확정 `study_records`와 정상 실행 중 `timer_runs`를 하나의
  `calculatedAt` 기준으로 합산한다.

현재 소속만 남기는 `team_members` 구조는 이 설계와 일치한다. 과거 시점의 팀
소속을 복원할 필요가 없으므로 `leftAt`, 소속 이력 테이블, snapshot, migration이
필요하지 않다.

## 기간별 범위

팀 필터는 학습시간의 계산 방식과 독립적이므로 기존 개인 랭킹의 모든 기간에
같게 적용할 수 있다.

| 기간 | 학습시간 계산 | 팀 필터 적용 시점 |
| --- | --- | --- |
| 오늘 | 확정 기록 + 정상 실행 중 타이머 | 조회 시점의 현재 팀 소속 |
| 일간 | 선택한 과거 집계일의 확정 기록 | 조회 시점의 현재 팀 소속 |
| 주간 | 기존 주간 확정 기간의 기록 합계 | 조회 시점의 현재 팀 소속 |
| 월간 | 기존 월간 확정 기간의 기록 합계 | 조회 시점의 현재 팀 소속 |

KST 04:00 경계, `calculatedAt`, `weekStartDate`, `includedThroughDate`, 실행 중
타이머 판정은 `StudyRanking-API-계약.md`의 기존 규칙을 그대로 재사용한다.

## 집계 정책

### 팀 내부 개인 랭킹

팀 필터를 순위 계산 전에 적용한다.

```text
cohortCandidates = ACTIVE STUDENT memberships in requested cohort
teamCandidates = cohortCandidates intersect current members of requested team
ranking = rank(teamCandidates by existing personal studySeconds)
```

- Team Module의 현재 소속 평면 매핑에 선택한 `teamId`가 없으면 빈 랭킹을 반환한다.
- 따라서 존재하지 않거나 해체됐거나 요청 기수와 다른 팀, 현재 팀원이 없는 팀은
  팀 내부 랭킹에서 동일하게 빈 후보로 처리한다.
- 팀 역할이 MASTER인지 MEMBER인지는 학습 랭킹 후보 여부에 영향을 주지 않는다.
- 학습시간이 0인 후보 제외, competition ranking, `maxRank`, 동점 안정 정렬은
  기존 개인 랭킹과 같다.
- `rankedMemberCount`와 순위는 반드시 팀 필터를 적용한 결과에서 계산한다.
- 별도의 팀 내부 학습시간 계산 로직은 두지 않는다.

### 팀 간 랭킹

기수 수강생의 기존 개인 학습시간을 현재 팀 소속으로 그룹화한다.

```text
cohortCandidates = ACTIVE STUDENT memberships in requested cohort
currentTeamMappings = current team membership of cohortCandidates
teamScore[teamId] = sum(personal studySeconds of mapped memberships)
ranking = rank(teams by teamScore)
```

- 해체된 팀과 팀이 없는 수강생은 팀 간 랭킹 집계에서 제외한다.
- 현재 유효한 수강생이 없거나 합계가 0인 팀은 순위에서 제외한다.
- 팀 인원수 자체는 점수에 별도 가중치를 주지 않는다.
- 점수 내림차순의 competition ranking을 사용한다.
- 동점 안에서는 `teamId ASC`로 안정 정렬한다.
- `maxRank`는 기존과 같이 반환 개수가 아니라 포함할 최대 순위다.

팀 점수는 다음 합계다.

```text
teamScore = sum(currentMember.studySeconds)
```

이 값은 “그 팀에서 진행한 공부 시간”이 아니라 “현재 팀원들이 해당 기간에
공부한 시간의 합계”다. 평균, 인원 보정, 팀 역할 가중치는 적용하지 않는다.

## 팀 이동과 과거 기간의 의미

일간·주간·월간도 조회 시점의 현재 팀 필터를 적용한다.

```text
8월 10일: 수강생이 A팀 소속일 때 3시간 학습
8월 11일: 수강생이 B팀으로 이동
8월 12일: 8월 월간 팀 랭킹 조회
결과: 해당 3시간은 현재 팀인 B팀의 후보 또는 합계에 포함
```

이는 과거 학습시간의 저장 귀속을 A팀에서 B팀으로 변경한 것이 아니다. 개인에게
계산된 3시간은 그대로 두고, 조회 시점에 B팀 소속이라는 필터를 적용한 결과다.

따라서 팀 이동 후 과거 기간의 팀 내부 개인 순위와 팀 간 순위가 달라질 수 있다.
이 변화는 현재 설계에서 정상 동작이다. 향후 “학습 당시 팀” 기준의 불변 결과가
필요해지면 현재 기능을 확장하지 않고, 소속 이력과 기간 귀속을 가진 별도 기능으로
설계한다.

## HTTP interface

기존 개인 랭킹처럼 기간별 path를 분리한다.

### 팀 내부 개인 랭킹

```http
GET /api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/today?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/daily/{date}?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/weekly/{week-start-date}?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/monthly/{month}?maxRank={N}
```

- 응답 구조는 기존 개인 랭킹을 재사용한다.
- `myRanking`은 요청자가 선택한 팀의 현재 후보일 때만 랭킹될 수 있다.
- `timerRunning`은 오늘 응답에서만 반환한다.

### 팀 간 랭킹

```http
GET /api/v1/cohorts/{cohort-id}/study-rankings/teams/today?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/study-rankings/teams/daily/{date}?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/study-rankings/teams/weekly/{week-start-date}?maxRank={N}
GET /api/v1/cohorts/{cohort-id}/study-rankings/teams/monthly/{month}?maxRank={N}
```

오늘 응답:

```json
{
  "aggregationDate": "2026-08-20",
  "calculatedAt": "2026-08-19T20:30:00Z",
  "rankedTeamCount": 2,
  "returnedEntryCount": 2,
  "entries": [
    {
      "rank": 1,
      "teamId": 100,
      "teamName": "A팀",
      "studySeconds": 28800
    }
  ],
  "myTeamRanking": {
    "ranked": true,
    "ranking": {
      "rank": 2,
      "teamId": 200,
      "teamName": "B팀",
      "studySeconds": 25200
    }
  }
}
```

- `studySeconds`는 현재 팀원들의 해당 기간 개인 학습시간 합계다.
- 팀 단위 `timerRunning`은 의미가 모호하므로 반환하지 않는다.
- 팀원 수나 실행 중인 팀원 수는 초기 응답에 추가하지 않는다.
- 현재 팀이 없는 요청자는 `myTeamRanking.ranked: false`로 처리한다.

## module과 seam

Ranking module은 Team persistence를 직접 조회하지 않는다. Team module은 현재
소속 관계를, Ranking module은 필터·학습시간 합산·순위 계산을 소유한다.

랭킹 요청의 동일 스냅샷은 Ranking QueryService 트랜잭션 경계에서만
`REPEATABLE_READ`로 보장한다. 공용 Study 집계 서비스와 관리자 통계 use case의
격리수준은 팀 랭킹 구현을 이유로 변경하지 않는다.

```text
Team Member Ranking Controller
  -> StudyRankingQueryService
      -> CohortAccessService
      -> CohortMembershipQueryService
      -> CurrentTeamMembershipQueryService
      -> StudyRecordAggregationQueryService
      -> CharacterGrowthService

Team Ranking Controller
  -> TeamStudyRankingQueryService
      -> CohortAccessService
      -> CohortMembershipQueryService
      -> CurrentTeamMembershipQueryService
      -> StudyRecordAggregationQueryService
```

### Team module의 최소 interface

`CurrentTeamMembershipQueryService`는 유효한 membership ID에 대응하는 현재 팀
소속만 배치로 반환한다.

```java
record CurrentTeamMembershipView(
        Long teamId,
        String teamName,
        Long cohortMembershipId
) {
}
```

- 입력: `cohortId`와 해당 기수의 유효한 membership ID 목록
- 출력: 해체되지 않은 현재 팀과 입력 membership의 평면 매핑
- Team module이 `teams.deleted_at`, `team_members`, 팀-기수 정합성을 숨긴다.
- Ranking module이 특정 `teamId` 필터와 팀별 그룹화를 수행한다.

팀 내부용 roster와 팀 간용 group을 별도 interface로 만들지 않는다. 하나의 현재
소속 배치 조회 결과로 두 호출부를 지원하고, 과거 소속 조회 interface도 만들지 않는다.

### competition ranking 계산

개인 순위와 팀 순위는 Ranking module 내부의 순수 계산 `CompetitionRanking`을
공유한다.

```text
rank(sorted scores) -> competition-ranked scores
```

- 외부 interface로 노출하지 않는다.
- 동점 순위, `maxRank` 경계, 안정 정렬을 한 곳에서 소유한다.
- 개인과 팀의 응답 조립은 각 QueryService에 남긴다.

## 조회 흐름

### 팀 내부 개인 랭킹

1. 요청자의 기수 수강생 조회 권한을 검증한다.
2. 요청 기수의 `ACTIVE STUDENT` membership을 일괄 조회한다.
3. 현재 팀 소속을 일괄 조회하고 선택한 `teamId`로 후보를 필터링한다.
4. 기존 기간별 학습시간 계산을 후보 membership에 적용한다.
5. 기존 개인 순위 규칙으로 순위와 `myRanking`을 조립한다.

### 팀 간 랭킹

1. 요청자의 기수 수강생 조회 권한을 검증한다.
2. 요청 기수의 `ACTIVE STUDENT` membership을 일괄 조회한다.
3. 현재 팀 소속을 일괄 조회해 membership을 팀별로 그룹화한다.
4. 기존 기간별 학습시간을 같은 기준 시각으로 일괄 계산한다.
5. 개인 학습시간을 현재 팀별로 합산한다.
6. 합계가 0보다 큰 팀에 competition ranking과 `maxRank`를 적용한다.
7. 요청자의 현재 팀을 기준으로 `myTeamRanking`을 조립한다.

모든 조회는 배치로 수행하고 팀별·membership별 N+1 조회를 허용하지 않는다.

## 권한 계약

| 조회 | 현재 권한 | 후속 검토 |
| --- | --- | --- |
| 팀 간 랭킹 | 요청 기수의 `ACTIVE STUDENT` | 없음 |
| 팀 내부 개인 랭킹 | 요청 기수의 `ACTIVE STUDENT` | 해당 팀원으로 제한할지 추후 결정 |

현재는 요청 기수의 모든 `ACTIVE STUDENT`가 팀 내부 개인 랭킹을 조회할 수 있다.
요청자가 선택한 팀의 현재 팀원이 아니면 `myRanking.ranked`는 `false`다. 팀원 전용
권한으로 변경할 경우 Application의 TODO 지점에서 요청 membership과 `teamId`의
현재 소속을 추가 검증한다.

## 구현 반영 상태

1. Team Module은 현재 팀 소속 평면 매핑 하나만 배치로 제공한다.
2. Ranking Module은 팀 내부 개인 랭킹의 `teamId` 필터와 팀 간 그룹화를 소유한다.
3. 오늘·일간·주간·월간은 기존 개인 랭킹의 기간 계산을 재사용한다.
4. 개인과 팀은 Ranking Module 내부 `CompetitionRanking` 계산을 공유한다.
5. 기간별 팀 내부 개인 랭킹과 팀 간 랭킹 endpoint를 각각 제공한다.
6. 모든 팀·membership 조회는 목록 단위 배치 호출로 수행한다.

소속 이력, ranking snapshot, 팀별 학습 기록 migration은 이 순서에 포함하지 않는다.

## 필수 검증 시나리오

- 팀 내부 개인 랭킹에서 선택한 팀의 현재 팀원만 후보가 됨
- 팀 필터를 적용한 뒤 `rankedMemberCount`, competition ranking, `maxRank`가 계산됨
- 같은 기수의 다른 팀원과 다른 기수 membership이 제외됨
- 팀 역할과 무관하게 `ACTIVE STUDENT`만 후보가 됨
- 팀 간 랭킹에서 현재 팀원들의 개인 학습시간이 팀별로 합산됨
- 팀이 없는 학생과 해체된 팀이 팀 간 랭킹에서 제외됨
- 오늘 실행 중 타이머가 현재 팀 필터 후 `calculatedAt` 기준으로 포함됨
- 만료·종료·폐기 타이머가 제외됨
- 일간·주간·월간은 확정 기록만 사용하고 `timerRunning`을 반환하지 않음
- 0초 개인과 0초 팀이 각 순위에서 제외됨
- 동점 팀이 competition ranking과 `teamId ASC` 정렬을 따름
- `maxRank` 경계의 동점 팀이 모두 반환됨
- 요청자의 팀이 `maxRank` 밖이어도 `myTeamRanking`은 별도로 반환됨
- 팀 이동 후 과거 기간을 조회하면 개인 학습시간이 현재 팀의 후보·합계에 포함됨
- 팀 이동이 `study_records` 또는 `timer_runs`의 팀 귀속 변경을 유발하지 않음

## 후속 결정

- 팀 내부 개인 랭킹 조회 권한을 해당 팀의 현재 팀원으로 제한할지 결정한다.
