CREATE TABLE learning_service.idempotency_receipts (
    scope_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_idempotency_receipts
        PRIMARY KEY (scope_id, idempotency_key),
    CONSTRAINT ck_idempotency_receipts_status
        CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_idempotency_receipts_completion
        CHECK (
            (status = 'PROCESSING' AND completed_at IS NULL) OR
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_idempotency_receipts_expires_at
    ON learning_service.idempotency_receipts (expires_at);
