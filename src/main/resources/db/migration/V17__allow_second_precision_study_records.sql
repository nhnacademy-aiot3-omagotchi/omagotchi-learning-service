ALTER TABLE learning_service.study_records
    DROP CONSTRAINT ck_study_records_minute_precision;

ALTER TABLE learning_service.study_records
    ADD CONSTRAINT ck_study_records_second_precision
        CHECK (
            start_time = date_trunc('second', start_time)
            AND end_time = date_trunc('second', end_time)
        );
