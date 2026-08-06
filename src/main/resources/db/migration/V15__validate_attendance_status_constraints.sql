ALTER TABLE learning_service.attendance_records
    VALIDATE CONSTRAINT ck_attendance_records_auto_status;

ALTER TABLE learning_service.attendance_records
    VALIDATE CONSTRAINT ck_attendance_records_final_status;

ALTER TABLE learning_service.attendance_change_logs
    VALIDATE CONSTRAINT ck_attendance_change_logs_previous_status;

ALTER TABLE learning_service.attendance_change_logs
    VALIDATE CONSTRAINT ck_attendance_change_logs_next_status;