# Statistics 쿼리 성능 검증과 리팩터링 근거

## 문서 상태

- 작성일: 2026-08-12
- 상태: 현재 구현 기준선 측정 완료, 리팩터링 후보 실험 완료, 실제 코드·Flyway 미반영
- 대상: `statistics` 모듈의 기수 통계와 수강생 통계 조회
- 데이터 기준: 삭제되지 않은 확정 `study_records`
- 제외 대상: 실행 중 `timer_runs`, ranking, team 통계, frontend

이 문서는 Statistics 쿼리를 추후 리팩터링할 때 변경 필요성과 효과를 판단하기 위한 근거다. 현재 실행 계획, 데이터 증가 시 병목, 임시 DB에서 검증한 인덱스 후보, 실제 구현 후 남겨야 할 재측정 항목을 구분하여 기록한다.

## 1. 결론

현재 쿼리는 결과 정합성과 소규모 조회 성능에는 문제가 없다. 다만 기수별 60일 기록 수가 같더라도 전체 기수와 전체 `study_records`가 늘어나면 다음 두 쿼리의 실행 시간이 증가한다.

1. 기수 기간 추이 `findDailyStudySeconds`
2. 수강생 통계 페이지 `findActiveStudentStatisticsPage`

두 쿼리는 대상 기수의 membership을 알고 있지만, 현재 인덱스가 집계에 필요한 `study_seconds`, `end_time`, record count 컬럼을 포함하지 않는다. PostgreSQL은 대상 membership별 heap 접근보다 기간 내 전체 `study_records` 순차 스캔이 저렴하다고 판단할 수 있다.

현재 단계에서 별도 일별 집계 테이블, materialized view 또는 `study_records.cohort_id` 반정규화를 도입할 근거는 부족하다. 먼저 기존 복합 인덱스의 키 순서를 유지한 커버링 인덱스 후보와 동치 count 표현을 적용하는 것이 변경 범위와 효과의 균형이 가장 좋다.

## 2. 현재 쿼리와 복잡도

아래 기호를 사용한다.

- `N`: 요청 기간에 포함되는 전체 기수의 활성 StudyRecord 수
- `R`: 요청 기간에 포함되는 대상 기수의 활성 StudyRecord 수
- `M`: 대상 기수의 활성 수강생 수
- `r`: 한 수강생의 요청 기간 StudyRecord 수
- `d`: 한 수강생의 특정 집계일 StudyRecord 수

| Repository 함수 | 주요 처리 | 현재 관측 복잡도 | 판단 |
| --- | --- | --- | --- |
| `summarizeToday` | 활성 수강생별 오늘 합산 후 Java에서 인원·구간 계산 | `O(M log N + 오늘 기록 수)` | 유지 가능 |
| `findDailyStudySeconds` | 기간 기록을 날짜별 합산 | 현재 실행 계획에서 `O(N)` | 전체 기수 증가 영향 |
| `findActiveStudentStatisticsPage` | 기간 기록 조인·수강생별 집계·집계값 정렬·pagination | `O(N + R log R + M log M)` | 우선 최적화 대상 |
| `countActiveStudents` | 활성 수강생 수 | membership index 범위 | 유지 가능 |
| `findActiveStudent` | 기수·membership 검증 | PK/index lookup | 유지 가능 |
| `summarizeActiveRecords` | 한 수강생 기간 합계·일수·건수·최근 시각 | `O(log N + r)` | 유지 가능 |
| `findMemberDailyStudySeconds` | 한 수강생 기간 일별 합산 | `O(log N + r)` | 유지 가능 |
| `findMemberDailyRecords` | 한 수강생 특정일 기록 정렬 | `O(log N + d)` | 유지 가능 |

멤버 페이지의 `LIMIT`와 `OFFSET`은 집계와 1차 정렬이 끝난 후 적용된다. 따라서 `size=20`이어도 대상 기간의 모든 수강생 기록을 먼저 집계해야 한다. frontend 페이지 캐시는 이미 조회한 페이지의 재요청은 줄일 수 있지만 최초 페이지 요청의 DB 집계량은 줄이지 않는다.

## 3. 측정 환경

### 3.1 공통 환경

- PostgreSQL: 18.1 Docker 임시 컨테이너
- Schema: 실제 Flyway V1~V20과 동일한 `learning_service` 구조
- Git branch와 기준 HEAD: `feature/timer`, `1e5d4841971f`
- 측정 대상 Statistics Repository: 기준 HEAD에 포함되지 않은 미커밋 신규 파일
- 기간: 2026-06-14부터 2026-08-12까지 60일
- 대상 기수: cohort id `1`
- 기수별 활성 수강생: 1,000명
- 수강생별 기록: 하루 1건, 60일간 60건
- 모든 측정 record: `deleted_at IS NULL`
- 측정 명령: `EXPLAIN (ANALYZE, BUFFERS, VERBOSE)`
- PostgreSQL 기본 `work_mem` 사용

측정 SQL은 다음 미커밋 파일에 작성된 QueryDSL과 함수 위 SQL 주석을 기준으로 변환했다.

- `src/main/java/site/omagotchi/learningservice/statistics/infrastructure/persistence/repository/CohortStatisticsQueryDslRepository.java`
- `src/main/java/site/omagotchi/learningservice/statistics/infrastructure/persistence/repository/MemberStatisticsQueryDslRepository.java`

따라서 기준 HEAD만 checkout해서는 같은 쿼리를 재현할 수 없다. 리팩터링 시에는 Statistics 구현이 포함된 실제 commit 또는 PR 번호를 이 문서의 구현 후 분석 기록에 추가하고 그 소스에서 SQL을 다시 추출해야 한다.

### 3.2 데이터 단계

| 단계 | 기수 수 | 전체 membership | 대상 기수 record | 전체 record |
| --- | ---: | ---: | ---: | ---: |
| 최초 기준선 | 1 | 1,000 | 60,000 | 60,000 |
| 확장성 검증 | 10 | 10,000 | 60,000 | 600,000 |

대상 기수 데이터는 두 단계에서 동일하다. 두 번째 단계는 다른 9개 기수의 기록만 추가하여 전체 테이블 증가가 한 기수 조회에 미치는 영향을 확인했다.

## 4. 현재 구현 측정 결과

### 4.1 60,000건 기준선

| 조회 | 실행 시간 | 핵심 실행 계획 |
| --- | ---: | --- |
| 오늘 요약 | 10.308ms | 수강생 1,000명과 오늘 기록 join |
| 기수 60일 추이 | 54.956ms | 60,000건 sequential scan, 60일 hash aggregate |
| 멤버 통계 첫 페이지 | 119.611ms | 60,000건 sort, external merge, 임시 디스크 4.36MB |
| 활성 수강생 수 | 0.815ms | membership bitmap index/heap scan |
| 활성 수강생 확인 | 0.049ms | membership index scan |
| 개인 60일 요약 | 0.559ms | 기존 StudyRecord 복합 index, 60건 |
| 개인 60일 일별 합산 | 0.504ms | 기존 StudyRecord 복합 index, 60건 |
| 개인 특정일 기록 | 0.035ms | 기존 StudyRecord 복합 index와 소규모 정렬 |

기존 Task 28에는 60,000건 멤버 조회에서 disk spill이 없었다고 기록되어 있다. 이번 현재 SQL 재측정에서는 4.36MB external merge가 발생했다. 실행 시점, fixture, cache와 세션 설정이 완전히 보존되지 않은 과거 단일 수치보다 이번 실행 계획의 scan row, buffer와 temp I/O를 추후 비교 기준으로 사용한다.

### 4.2 전체 600,000건 확장성 측정

| 조회 | 실행 시간 | 핵심 실행 계획 |
| --- | ---: | --- |
| 오늘 요약 | 15.655ms | 대상 membership별 기존 복합 index lookup |
| 기수 60일 추이 | 93.554ms | 전체 600,000건 parallel sequential scan |
| 멤버 통계 첫 페이지 | 293.611ms | 전체 600,000건 sequential scan 후 대상 60,000건 집계, 4.36MB spill |
| 활성 수강생 수 | 0.503ms | membership index 사용 |
| 활성 수강생 확인 | 0.030ms | membership index 사용 |
| 개인 60일 요약 | 1.436ms | 기존 StudyRecord 복합 index 사용 |
| 개인 60일 일별 합산 | 0.121ms | 기존 StudyRecord 복합 index 사용 |
| 개인 특정일 기록 | 0.059ms | 기존 StudyRecord 복합 index 사용 |

대상 기수 record 수가 60,000건으로 동일한데도 추이와 멤버 페이지 실행 시간이 증가했다. 따라서 두 쿼리의 주요 확장 단위는 대상 기수 record `R`만이 아니라 기간 내 전체 record `N`이다.

## 5. 리팩터링 후보 실험

이 절의 변경은 임시 PostgreSQL에만 적용했다. 저장소 코드와 Flyway에는 반영하지 않았다.

### 5.1 기존 인덱스

```sql
CREATE INDEX idx_study_records_membership_date_time
    ON learning_service.study_records (
        cohort_membership_id,
        aggregation_date,
        start_time
    )
    WHERE deleted_at IS NULL;
```

600,000건 fixture에서 크기는 26MB였다.

### 5.2 전체 컬럼 포함 후보

첫 실험은 페이지 쿼리를 수정하지 않기 위해 `id`까지 포함했다.

```sql
CREATE INDEX idx_bench_study_records_membership_date_cover
    ON learning_service.study_records (
        cohort_membership_id,
        aggregation_date
    )
    INCLUDE (study_seconds, id, end_time)
    WHERE deleted_at IS NULL;
```

| 항목 | 결과 |
| --- | --- |
| 인덱스 크기 | 39MB, 기존 대비 약 50% 증가 |
| 기수 추이 | 18.229ms |
| 멤버 페이지 | 29.189ms |
| 실행 계획 | membership 기준 index-only scan |

성능은 개선됐지만 UUID `id`를 포함하고 기존 일별 정렬 키인 `start_time`을 제거한다. 영구 후보로는 크기와 기존 조회 호환성이 불리하여 채택하지 않는다.

### 5.3 권장 후보

기존 키 순서를 유지하고 집계에 필요한 값만 포함한다.

```sql
CREATE INDEX idx_bench_study_records_stats_cover
    ON learning_service.study_records (
        cohort_membership_id,
        aggregation_date,
        start_time
    )
    INCLUDE (study_seconds, end_time)
    WHERE deleted_at IS NULL;
```

페이지의 기록 건수는 다음과 같이 계산한다.

```sql
COUNT(sr.study_seconds)
```

`study_records.study_seconds`와 `id`는 모두 `NOT NULL`이다. `LEFT JOIN`에서 record가 없으면 두 표현 모두 null을 세지 않으므로 `COUNT(sr.id)`와 `COUNT(sr.study_seconds)`의 결과는 같다. 이 변경으로 UUID를 커버링 인덱스에 포함하지 않아도 된다.

600,000건 fixture에서 후보 크기는 34MB로 기존보다 8MB, 약 31% 증가했다.

### 5.4 동일 조건 전후 비교

캐시 편차를 줄이기 위해 600,000건 fixture를 `ANALYZE`한 뒤, 핵심 세 쿼리를 기존 인덱스 조건과 권장 후보 조건에서 각각 동시에 재실행했다.

| 쿼리 | 기존 인덱스 | 권장 후보 | 변화 |
| --- | ---: | ---: | ---: |
| 오늘 요약 | 2.259ms | 2.122ms | 유의미한 변화 없음 |
| 기수 60일 추이 | 33.464ms | 18.188ms | 약 46% 감소 |
| 멤버 통계 첫 페이지 | 118.782ms | 34.332ms | 약 71% 감소 |

권장 후보에서는 추이와 멤버 페이지가 대상 기수의 membership에서 시작하여 `study_records`를 index-only scan했다. 멤버 페이지의 4.36MB external merge도 발생하지 않았다.

## 6. 권장 리팩터링 범위

### 6.1 우선 적용 후보

1. 과거 V3 migration을 수정하지 않고 신규 Flyway migration으로 커버링 인덱스를 만든다.
2. 배포 중 index 생성 방식과 lock 허용 범위를 별도로 결정한다. `CREATE INDEX CONCURRENTLY` 사용 여부는 Flyway transaction 설정과 함께 검토한다.
3. 새 인덱스 검증 전 기존 인덱스를 먼저 제거하지 않는다.
4. `MemberStatisticsQueryDslRepository`의 record count를 `studyRecord.studySeconds.count()`로 변경한다.
5. `summarizeActiveRecords`를 포함한 다른 `COUNT(studyRecord.id)`도 동일한 `NOT NULL` 계약과 응답 의미가 맞는지 확인하되, 통계 페이지 변경과 불필요하게 묶지 않는다.
6. 결과 정합성 테스트와 PostgreSQL 실행 계획을 통과한 뒤 기존 인덱스 제거 또는 새 인덱스로의 대체 여부를 결정한다.

### 6.2 당장 도입하지 않는 변경

- 일별·수강생별 집계 테이블
- materialized view
- `study_records`에 `cohort_id` 중복 저장
- 전역 `work_mem` 증가
- 전체 Statistics 쿼리의 native SQL 전환
- 개인 overview 쿼리 강제 통합

현재 개인 조회는 기존 인덱스로 2ms 미만이며, `countActiveStudents`도 1ms 미만이다. 이 쿼리들을 합치면 성능 이익보다 Application 계약과 QueryDSL 조립 복잡도가 커질 가능성이 높다.

## 7. 리팩터링 시 주의사항

- 커버링 인덱스는 저장 공간과 insert/update 비용을 늘린다.
- `study_seconds` 또는 `end_time` 변경도 인덱스 갱신을 발생시킨다.
- index-only scan은 PostgreSQL visibility map 상태에 영향을 받으므로 쓰기가 빈번한 운영 환경에서는 heap fetch가 다시 발생할 수 있다.
- `7d~60d` 제한은 날짜 수를 제한하지만 하루 record 개수를 제한하지는 않는다.
- aggregate sort pagination은 page 크기와 관계없이 정렬 기준을 계산하기 위해 모든 대상 수강생을 집계한다.
- `cohortMembershipId` 정렬만 별도 2단계 pagination으로 최적화할 수 있지만, 기본 정렬이 기간 학습시간이고 나머지 정렬도 집계값 기반이므로 현재는 별도 분기를 만들지 않는다.
- frontend 캐시는 동일 page 재방문 비용을 줄일 뿐, 첫 요청의 DB 비용을 해결하지 않는다.

## 8. 구현 후 검증 기준

아래 기준은 외부 SLA가 아니라 이번 리팩터링의 잠정 회귀 기준이다. 실제 운영 성능 목표가 정해지면 그 기준을 우선한다.

### 8.1 정합성

- [ ] 기존 인덱스와 새 인덱스 조건에서 8개 Repository 함수 결과가 동일하다.
- [ ] `COUNT(id)`와 `COUNT(study_seconds)`가 기록 0건, 여러 건, soft-delete 포함 fixture에서 동일하다.
- [ ] 활성 `STUDENT`만 포함하고 다른 기수, 종료 membership, mentor와 삭제 record가 포함되지 않는다.
- [ ] 동률 pagination의 `cohortMembershipId ASC` 안정 정렬이 유지된다.
- [ ] `7d`, 중간값, `60d`의 합계와 빈 날짜 0초 조립이 유지된다.

### 8.2 실행 계획

- [ ] 10개 기수·전체 600,000건에서 추이 쿼리가 전체 `study_records` sequential scan을 사용하지 않는다.
- [ ] 멤버 페이지가 대상 기수 60,000건만 읽는다.
- [ ] 멤버 페이지에서 temp file 또는 external merge가 발생하지 않는다.
- [ ] 추이와 멤버 페이지가 새 인덱스의 index-only scan을 사용한다.
- [ ] 개인 일별 기록의 membership/date 조건과 `start_time` 정렬에 회귀가 없다.

### 8.3 성능과 저장 비용

- [ ] 비교 대상 commit 또는 PR과 측정 SQL을 함께 기록한다.
- [ ] 동일 fixture와 동일 측정 순서에서 기존·변경 후 결과를 각각 3회 이상 측정하고 중앙값을 기록한다.
- [ ] DB 쿼리뿐 아니라 Repository와 HTTP 요청의 p50/p95를 구분해 기록한다.
- [ ] 동시 dashboard 요청과 StudyRecord 쓰기가 함께 있을 때 latency를 확인한다.
- [ ] 새 인덱스 크기와 `pg_stat_user_indexes` 사용량을 기록한다.
- [ ] StudyRecord 생성·수정 처리량의 회귀를 확인한다.

### 8.4 배포 안전성

- [ ] 빈 PostgreSQL에서 전체 Flyway migration이 성공한다.
- [ ] Hibernate `ddl-auto=validate`가 성공한다.
- [ ] 기존 데이터가 있는 PostgreSQL에서 migration 소요 시간과 lock을 확인한다.
- [ ] migration 실패 시 기존 인덱스가 유지되는지 확인한다.
- [ ] `./mvnw clean verify`와 Statistics PostgreSQL IT가 성공한다.

## 9. 실제 구현 후 분석 기록

리팩터링을 구현한 작업에서 이 절을 갱신한다. 실험 결과를 실제 적용 결과로 오인하지 않도록 비어 있는 값은 임의로 채우지 않는다.

| 항목 | 구현 후 기록 |
| --- | --- |
| 구현 일자 | 미구현 |
| commit 또는 PR | 미구현 |
| Flyway migration | 미구현 |
| 실제 index 정의 | 미구현 |
| QueryDSL 변경 | 미구현 |
| fixture와 측정 환경 | 미구현 |
| 기수 추이 before / after | 미구현 |
| 멤버 페이지 before / after | 미구현 |
| temp I/O before / after | 미구현 |
| index 크기 before / after | 미구현 |
| 쓰기 성능 영향 | 미구현 |
| 전체 테스트 결과 | 미구현 |
| 최종 결정 | 미구현 |

## 10. 측정 한계

- Docker 로컬 PostgreSQL 단일 호스트 측정이며 application과 DB 사이의 실제 network latency가 없다.
- 최초 확장성 측정과 인덱스 비교 측정은 cache 상태가 다르므로 절대 시간끼리 직접 비교하지 않는다.
- 인덱스 전후 비교에서는 핵심 세 쿼리를 동시에 실행했지만 반복 측정 중앙값과 장시간 부하는 수행하지 않았다.
- fixture는 수강생당 하루 1건이다. 여러 번의 짧은 학습 기록이 생성되는 운영 분포에서는 `R`이 더 커질 수 있다.
- 실행 중 TimerRun은 현재 계약대로 포함하지 않았다.
- 측정 종료 후 임시 PostgreSQL 컨테이너와 실험 인덱스는 삭제했다.

따라서 이 문서의 수치는 변경 방향을 선택하는 근거이며 운영 용량 계획의 최종 수치가 아니다.
