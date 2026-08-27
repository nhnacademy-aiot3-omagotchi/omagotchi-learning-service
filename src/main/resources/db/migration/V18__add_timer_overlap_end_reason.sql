ALTER TABLE learning_service.timer_runs
    DROP CONSTRAINT ck_timer_runs_state;

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
        );
