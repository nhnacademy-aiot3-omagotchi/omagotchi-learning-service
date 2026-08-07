ALTER TABLE learning_service.study_records
    ADD CONSTRAINT ck_study_records_minute_precision
        CHECK (
            start_time = date_trunc('minute', start_time)
                AND end_time = date_trunc('minute', end_time)
            );