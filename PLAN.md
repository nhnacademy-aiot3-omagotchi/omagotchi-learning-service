# Plan: timer_runs 기반 타이머 실행 구현

## Problem

- 현재 상태: `timer_runs` 영속성과 Command·Query Service TODO 스켈레톤, `501 NOT_IMPLEMENTED` Controller 골격
- 구현 목표: 시작·현재 상태·정상 정지·사용자 폐기·N시간 자동 폐기의 서버 기준 처리
- 저장 목표: 생성 한 번과 종료 한 번만 허용하는 `timer_runs` 실행 원본
- 범위 제약: `command_receipts`와 다른 신규 테이블·Entity, 하트비트, 복구, 오프라인 동기화 제외

## Solution

- 신규 저장 구조: 다음 순방향 Flyway `V5`와 `TimerRun` Entity 하나
- 소유권 경계: `CohortAccessService.requireActiveMembershipId()`의 검증된 `cohort_membership_id`
- Application 경계: `TimerCommandService` 쓰기와 `TimerQueryService` 읽기 분리
- 동시성 경계: 기존 `StudyWriteLock`의 PostgreSQL transaction-scoped advisory lock 재사용
- 시간 경계: 기존 `DateTimeProvider`와 서버 설정 `timer.max-duration` 기반 계산
- 종료 경계: `ended_at IS NULL` 조건부 갱신과 변경 건수 1 판정
- 기록 경계: 기존 `study_records`만 활용하는 정상 정지 원자적 확정
- 명령 경계: `commandId` Application 매개변수와 후속 TODO만 유지

## Tasks

- [x] [01-create-timer-run-persistence](tasks/01-create-timer-run-persistence.md) - `timer_runs` Migration·Entity·Repository 구현
- [x] [02-implement-timer-start-and-current](tasks/02-implement-timer-start-and-current.md) - 시작과 읽기 전용 현재 상태 구현
- [x] [03-implement-timer-discard-and-expiration](tasks/03-implement-timer-discard-and-expiration.md) - 사용자 폐기와 자동 만료 구현
- [ ] [04-build-timer-record-confirmation](tasks/04-build-timer-record-confirmation.md) - 기존 StudyRecord 기반 분할·병합·거절 구현
- [ ] [05-integrate-timer-stop](tasks/05-integrate-timer-stop.md) - 정상 정지와 공부 기록의 원자적 통합
- [x] [06-connect-timer-api](tasks/06-connect-timer-api.md) - 네 가지 타이머 호출과 DTO·오류 연결
- [ ] [07-verify-timer-concurrency-and-migration](tasks/07-verify-timer-concurrency-and-migration.md) - PostgreSQL 동시성·Flyway·롤백 최종 검증

## Dependencies

- `TMR-003` 적용: `cohort_membership_id`별 활성 실행 1개
- 최대 실행 시간 설정: `timer.max-duration` 값과 운영 허용 범위 확정
- 0초 정상 정지: `STOP`, `measured_seconds=0`, 공부 기록 없음 결과 확정
- KST 04:00 분할: 정수 초 나머지 배분 순서 확정
- 공개 API: 영수증 구현 전 Controller 활성화 여부와 정지·폐기 대상 ID 전달 위치 확정
- 기록 정책 거절 API: 확정된 `STOP` 결과의 HTTP 상태와 응답 본문 확정
- 동시성 ADR: `0007-cohort-membership-write-serialization-policy` 수용 여부 확정

## Notes

- 요구사항 기준: [타이머 실행 및 상태](../docs/10-specifications/03-study/01-타이머-실행-및-상태.md)
- DB 기준: [Task 01 영속성](tasks/01-create-timer-run-persistence.md)과 `V10__create_timer_runs.sql`
- 구현 순서 기준: [타이머 개발 로드맵 Part 3](../docs/99-temporary/timer/개발-로드맵.md)
- 신규 의존성 추가 없음
- 기존 사용자 변경 파일 보존
- 구현 완료 기준: 계획 문서 체크박스가 아닌 각 작업의 실행 검증 통과
