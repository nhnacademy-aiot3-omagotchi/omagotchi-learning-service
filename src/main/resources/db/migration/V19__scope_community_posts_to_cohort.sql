-- 커뮤니티 게시판을 기수 소속으로 확정한다.
ALTER TABLE learning_service.community_posts
    DROP COLUMN scope,
    ALTER COLUMN cohort_id SET NOT NULL;

DROP INDEX learning_service.ix_community_posts_visible_list;

DROP INDEX learning_service.ix_community_posts_search;

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
