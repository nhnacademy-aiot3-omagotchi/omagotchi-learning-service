-- 연동 해제(soft delete)한 행이 telegram_chat_id·telegram_user_id·user_id의 유니크 자리를
-- 계속 차지해, 해제 뒤에는 그 텔레그램 계정을 아무도 다시 연동할 수 없었다.
--
-- 이 프로젝트는 같은 문제를 이미 부분 유니크로 푼다 — uq_teams_active_name(WHERE deleted_at
-- IS NULL), uq_team_members_membership, uq_cohort_memberships_active_student. 이 테이블만
-- 전체 유니크로 남아 있던 것을 같은 패턴으로 맞춘다.
--
-- 부분 유니크 생성은 CREATE UNIQUE INDEX CONCURRENTLY라 Transaction 안에서 실행할 수 없어
-- V23으로 분리했다. 한 파일에 두면 executeInTransaction=false가 이 DROP에도 적용되어,
-- 인덱스 생성이 실패했을 때 제약이 사라진 채로 남는다.

-- 활성 행에 중복이 있으면 V23의 인덱스 생성이 실패한다. CONCURRENTLY는 예외 없이 INVALID
-- 인덱스를 남기고 지나가므로 — 유니크가 강제되지 않는데 눈에 띄지도 않는다 — 여기서 먼저 막는다.
DO $$
DECLARE
    duplicated INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO duplicated
      FROM (
            SELECT 1
              FROM learning_service.telegram_user_links
             WHERE disconnected_at IS NULL
             GROUP BY telegram_chat_id
            HAVING COUNT(*) > 1
            UNION ALL
            SELECT 1
              FROM learning_service.telegram_user_links
             WHERE disconnected_at IS NULL
             GROUP BY telegram_user_id
            HAVING COUNT(*) > 1
            UNION ALL
            SELECT 1
              FROM learning_service.telegram_user_links
             WHERE disconnected_at IS NULL
             GROUP BY user_id
            HAVING COUNT(*) > 1
      ) conflicts;

    IF duplicated > 0 THEN
        RAISE EXCEPTION
            '활성 telegram_user_links 중복 %건. 대상을 정리한 뒤 다시 배포한다.', duplicated;
    END IF;
END $$;

ALTER TABLE learning_service.telegram_user_links
    DROP CONSTRAINT uq_telegram_user_links_chat,
    DROP CONSTRAINT uq_telegram_user_links_telegram_user,
    DROP CONSTRAINT uq_telegram_user_links_user;
