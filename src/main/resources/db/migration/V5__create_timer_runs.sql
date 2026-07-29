CREATE TABLE learning_service.timer_runs (
    id UUID NOT NULL,
    cohort_membership_id BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    measured_seconds BIGINT,
    end_reason VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_timer_runs PRIMARY KEY (id),
    CONSTRAINT ck_timer_runs_end_reason
        CHECK (end_reason IN ('STOP', 'DISCARD', 'EXPIRED'))
);

CREATE UNIQUE INDEX uq_timer_runs_active_membership
    ON learning_service.timer_runs (cohort_membership_id)
    WHERE ended_at IS NULL;

CREATE INDEX idx_timer_runs_membership_created_at
    ON learning_service.timer_runs (cohort_membership_id, created_at DESC);

CREATE INDEX idx_timer_runs_active_started_at
    ON learning_service.timer_runs (started_at)
    WHERE ended_at IS NULL;
