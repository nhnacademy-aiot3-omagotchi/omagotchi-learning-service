ALTER TABLE learning_service.study_records
    ADD CONSTRAINT ck_study_records_second_precision
        CHECK (
            start_time = date_trunc('second', start_time)
            AND end_time = date_trunc('second', end_time)
        )
        NOT VALID;

ALTER TABLE learning_service.study_records
    DROP CONSTRAINT ck_study_records_minute_precision;

ALTER TABLE learning_service.timer_runs
    RENAME CONSTRAINT ck_timer_runs_state
        TO ck_timer_runs_state_legacy;

ALTER TABLE learning_service.timer_runs
    ADD CONSTRAINT ck_timer_runs_state
        CHECK (
            (
                ended_at IS NULL
                AND measured_seconds IS NULL
                AND end_reason IS NULL
            )
            OR (
                ended_at IS NOT NULL
                AND end_reason IS NOT NULL
                AND end_reason = 'STOP'
                AND measured_seconds IS NOT NULL
            )
            OR (
                ended_at IS NOT NULL
                AND end_reason IS NOT NULL
                AND end_reason IN ('OVERLAP', 'DISCARD', 'EXPIRED')
                AND measured_seconds IS NULL
            )
        )
        NOT VALID;

ALTER TABLE learning_service.timer_runs
    DROP CONSTRAINT ck_timer_runs_state_legacy;
