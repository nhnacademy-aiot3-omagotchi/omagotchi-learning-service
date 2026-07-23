CREATE TABLE learning_service.study_records
(
    id                   UUID        PRIMARY KEY,
    cohort_membership_id BIGINT      NOT NULL,
    aggregation_date     DATE        NOT NULL,
    start_time           TIMESTAMPTZ NOT NULL,
    end_time             TIMESTAMPTZ NOT NULL,
    study_seconds        BIGINT      NOT NULL,

    deleted_at           TIMESTAMPTZ,

    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    /* 서버가 먼저 업무 오류로 검증하고, DB는 잘못된 쓰기를 막는 최종 방어선으로 사용한다. */
    -- 시작 및 종료 시간 검증
    CONSTRAINT ck_study_records_time
        CHECK (start_time < end_time),

    -- 분 단위로 저장되었는지 검증 (선택 고려)
    CONSTRAINT ck_study_records_minute_precision
        CHECK (
            start_time = date_trunc('minute', start_time)
            AND end_time = date_trunc('minute', end_time)
        ),

    -- study_seconds 범위 검증
    CONSTRAINT ck_study_records_seconds
        CHECK (
            study_seconds > 0
            AND study_seconds <= EXTRACT(EPOCH FROM (end_time - start_time))
        ),

    CONSTRAINT ck_study_records_version
        CHECK (version >= 0)
);

-- 삭제되지 않은 소속별 기록과 집계 일자 범위를 기준으로 한 복합 인덱스
CREATE INDEX idx_study_records_membership_date_time
    ON learning_service.study_records (
        cohort_membership_id,
        aggregation_date,
        start_time
    )
    WHERE deleted_at IS NULL;
