ALTER TABLE learning_service.study_records
    VALIDATE CONSTRAINT ck_study_records_second_precision;

ALTER TABLE learning_service.timer_runs
    VALIDATE CONSTRAINT ck_timer_runs_state;
