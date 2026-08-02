-- BIGINT 동등 비교와 시간 범위 겹침 연산을 하나의 GiST 제약에서 사용한다.
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

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

    -- 분 단위로 저장되었는지 검증 (삭제됨)

    -- study_seconds 범위 검증
    CONSTRAINT ck_study_records_seconds
        CHECK (
            study_seconds > 0
            AND study_seconds <= EXTRACT(EPOCH FROM (end_time - start_time))
        ),

    CONSTRAINT ck_study_records_version
        CHECK (version >= 0),

    -- 같은 기수 소속의 활성 기록은 반개구간 [start_time, end_time)이 겹칠 수 없다.
    -- 기본은 문장 종료 시 검사하고, 다중 청크 교체 트랜잭션에서만 명시적으로 지연할 수 있다.
    CONSTRAINT ex_study_records_no_overlap
        EXCLUDE USING gist (
            cohort_membership_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        )
        WHERE (deleted_at IS NULL)
        DEFERRABLE INITIALLY IMMEDIATE
);

-- 삭제되지 않은 소속별 기록과 집계 일자 범위를 기준으로 한 복합 인덱스
CREATE INDEX idx_study_records_membership_date_time
    ON learning_service.study_records (
        cohort_membership_id,
        aggregation_date,
        start_time
    )
    WHERE deleted_at IS NULL;
