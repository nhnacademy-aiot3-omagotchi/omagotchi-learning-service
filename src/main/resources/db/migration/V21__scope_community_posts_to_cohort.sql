-- 커뮤니티 게시판을 기수 소속으로 확정한다.
-- scope를 먼저 지우면 scope를 포함한 인덱스도 함께 자동 삭제되므로,
-- 인덱스를 먼저 명시적으로 내린다.
DROP INDEX learning_service.ix_community_posts_visible_list;

DROP INDEX learning_service.ix_community_posts_search;

ALTER TABLE learning_service.community_posts
    DROP COLUMN scope,
    ALTER COLUMN cohort_id SET NOT NULL;

CREATE INDEX ix_community_posts_cohort_list
    ON learning_service.community_posts (
        cohort_id,
        created_at DESC,
        id DESC
    )
    WHERE deleted_at IS NULL AND NOT pinned;

CREATE UNIQUE INDEX uq_community_posts_pinned
    ON learning_service.community_posts (cohort_id)
    WHERE pinned AND deleted_at IS NULL;
