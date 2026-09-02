-- 커뮤니티 게시판을 기수 소속으로 확정한다.
--
-- scope(GLOBAL/COHORT)는 전사 공지 하나를 위해 존재했고, 그 때문에 모든 조회에
-- "GLOBAL이거나 (COHORT이면서 내 기수)" 분기가 붙었다. 자유글은 이미 COHORT를
-- 강제받고 있었으므로 GLOBAL의 유일한 용도가 전사 공지였다. 전사 공지를 걷어내면
-- 게시글은 언제나 특정 기수에 속하므로 cohort_id만으로 가를 수 있다.
-- 공지와 자유글의 구분은 기존 type(NOTICE/FREE)이 그대로 담당한다.

-- GLOBAL 게시글은 cohort_id가 NULL이라 NOT NULL 전환 대상이 될 수 없다.
-- 운영(main)에 배포되지 않은 기능이므로 정리한다. 첨부 metadata는
-- fk_community_post_attachments_post의 ON DELETE CASCADE로 함께 지워지지만,
-- 저장소에 남는 실제 파일은 community.attachments.storage-root에서 별도로 정리해야 한다.
DELETE FROM learning_service.community_posts WHERE scope = 'GLOBAL';

ALTER TABLE learning_service.community_posts
    DROP CONSTRAINT ck_community_posts_scope_cohort,
    DROP CONSTRAINT ck_community_posts_scope,
    ALTER COLUMN cohort_id SET NOT NULL,
    DROP COLUMN scope;

-- 목록 조회가 항상 cohort_id로 시작하므로 scope가 선두인 인덱스를 교체한다.
DROP INDEX learning_service.ix_community_posts_visible_list;

-- 기수 필터가 항상 앞서면서 created_at 선두 인덱스는 쓰이지 않는다.
DROP INDEX learning_service.ix_community_posts_search;

-- 목록 쿼리(cohort_id = ? [and type = ?] order by pinned desc, created_at desc, id desc)와
-- 컬럼 순서를 그대로 맞춘다. 정렬까지 인덱스로 처리된다.
CREATE INDEX ix_community_posts_cohort_list
    ON learning_service.community_posts (
        cohort_id,
        type,
        pinned DESC,
        created_at DESC,
        id DESC
    )
    WHERE deleted_at IS NULL;

-- ix_community_posts_cohort(cohort_id 단일)는 부분 인덱스가 못 받는
-- FK 확인용이므로 남긴다.
