ALTER TABLE learning_service.attendance_records
    DROP CONSTRAINT ck_attendance_records_auto_status,
    ADD CONSTRAINT ck_attendance_records_auto_status
        CHECK (auto_status IN (
            'PENDING',
            'PRESENT',
            'LATE',
            'ABSENT',
            'LEFT_EARLY',
            'LATE_LEFT_EARLY',
            'MISSING_CHECK_OUT'
        ));

ALTER TABLE learning_service.attendance_records
    DROP CONSTRAINT ck_attendance_records_final_status,
    ADD CONSTRAINT ck_attendance_records_final_status
        CHECK (final_status IN (
            'PENDING',
            'PRESENT',
            'LATE',
            'ABSENT',
            'LEFT_EARLY',
            'LATE_LEFT_EARLY',
            'MISSING_CHECK_OUT'
        ));

ALTER TABLE learning_service.attendance_change_logs
    DROP CONSTRAINT ck_attendance_change_logs_previous_status,
    ADD CONSTRAINT ck_attendance_change_logs_previous_status
        CHECK (previous_status IN (
            'PENDING',
            'PRESENT',
            'LATE',
            'ABSENT',
            'LEFT_EARLY',
            'LATE_LEFT_EARLY',
            'MISSING_CHECK_OUT'
        ));

ALTER TABLE learning_service.attendance_change_logs
    DROP CONSTRAINT ck_attendance_change_logs_next_status,
    ADD CONSTRAINT ck_attendance_change_logs_next_status
        CHECK (next_status IN (
            'PENDING',
            'PRESENT',
            'LATE',
            'ABSENT',
            'LEFT_EARLY',
            'LATE_LEFT_EARLY',
            'MISSING_CHECK_OUT'
        ));
