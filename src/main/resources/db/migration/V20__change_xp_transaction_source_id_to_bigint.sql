ALTER TABLE learning_service.xp_transactions
    ALTER COLUMN source_id TYPE BIGINT
    USING source_id::BIGINT;
