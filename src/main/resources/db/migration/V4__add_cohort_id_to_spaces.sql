-- RM-24
-- 공간의 관리 주체 기수를 표현한다.
-- 초기 공간 및 아직 관리 기수가 지정되지 않은 공간은 NULL을 허용한다.

ALTER TABLE learning_service.spaces
    ADD COLUMN cohort_id BIGINT;

ALTER TABLE learning_service.spaces
    ADD CONSTRAINT fk_spaces_cohort_id
        FOREIGN KEY (cohort_id)
        REFERENCES learning_service.cohorts (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_spaces_cohort_id
    ON learning_service.spaces (cohort_id);

COMMENT ON COLUMN learning_service.spaces.cohort_id IS
    '공간의 관리 주체 기수 ID. 초기 공간 또는 미배정 공간은 NULL.';
