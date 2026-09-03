ALTER TABLE learning_service.user_daily_quests
    VALIDATE CONSTRAINT ck_user_daily_quests_target_source;

ALTER TABLE learning_service.user_daily_quests
    VALIDATE CONSTRAINT ck_user_daily_quests_target_seconds;

ALTER TABLE learning_service.user_daily_quests
    VALIDATE CONSTRAINT ck_user_daily_quests_model_version;
