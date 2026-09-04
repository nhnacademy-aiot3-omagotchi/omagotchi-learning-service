-- 연동 해제(soft delete)한 행이 telegram_chat_id·telegram_user_id·user_id의 유니크 자리를
-- 계속 차지해, 해제 뒤에는 그 텔레그램 계정을 아무도 다시 연동할 수 없었다.
--
-- 이 프로젝트는 같은 문제를 이미 부분 유니크로 푼다 — uq_teams_active_name(WHERE deleted_at
-- IS NULL), uq_team_members_membership, uq_cohort_memberships_active_student. 이 테이블만
-- 전체 유니크로 남아 있던 것을 같은 패턴으로 맞춘다.
--
-- V22에서 대체 부분 유니크 인덱스(uq_telegram_user_links_active_*)를 먼저 만들어 둔 뒤에만
-- 이 전체 유니크 제약을 지운다. 순서를 바꾸면(제약을 먼저 지우면) 대체 인덱스가 완성되기
-- 전까지 활성 행 중복을 막는 DB 제약이 전혀 없는 구간이 생긴다 — V22 주석 참고.
--
-- 이 시점엔 전체 유니크 제약이 V22가 끝날 때까지 계속 걸려 있었으므로, 활성이든 해제든
-- 이 테이블에 telegram_chat_id·telegram_user_id·user_id 중복이 존재할 수 없다. 그래서
-- 지우기 전 별도의 중복 검사가 필요 없다 — 검사할 대상 자체가 있을 수 없다.
--
-- DROP CONSTRAINT는 ACCESS EXCLUSIVE 잠금을 요구한다. 이 테이블을 잡고 있는 트랜잭션이
-- 있으면 잠금 대기열에 걸려 무한정 기다리고, 그 뒤로 들어오는 다른 쿼리까지 줄줄이 막힌다.
-- lock_timeout으로 대기 시간을 제한해 그 상황이면 이 Migration이 실패하게 한다 — 배포를
-- 조용히 막는 대신 확실하게 실패시켜 재시도하게 하는 편이 낫다. SET LOCAL이라 이
-- Transaction(이 Migration) 안에서만 적용되고 끝나면 원래 설정으로 돌아간다.
SET LOCAL lock_timeout = '5s';

ALTER TABLE learning_service.telegram_user_links
    DROP CONSTRAINT uq_telegram_user_links_chat,
    DROP CONSTRAINT uq_telegram_user_links_telegram_user,
    DROP CONSTRAINT uq_telegram_user_links_user;
