-- 예측 기반 학습 시간 퀘스트를 위한 컬럼.
-- target_count는 1(했다/안 했다)로 유지하고, 실제 목표 시간은 target_seconds가 갖는다.
-- 기존 횟수형 퀘스트는 target_seconds가 NULL이며 동작이 바뀌지 않는다.
ALTER TABLE learning_service.user_daily_quests
    ADD COLUMN target_seconds INTEGER NULL,
    ADD COLUMN target_source VARCHAR(20) NOT NULL DEFAULT 'TEMPLATE',
    ADD COLUMN model_version VARCHAR(50) NULL;

-- 모델 결과와 규칙 결과를 구분해 기록한다(ADR prediction/0002).
ALTER TABLE learning_service.user_daily_quests
    ADD CONSTRAINT ck_user_daily_quests_target_source
        CHECK (target_source IN ('TEMPLATE', 'MODEL', 'RULE_B2', 'DEFAULT'))
        NOT VALID;

ALTER TABLE learning_service.user_daily_quests
    ADD CONSTRAINT ck_user_daily_quests_target_seconds
        CHECK (target_seconds IS NULL OR target_seconds > 0)
        NOT VALID;

-- 모델로 산정한 퀘스트만 모델 버전을 남긴다.
ALTER TABLE learning_service.user_daily_quests
    ADD CONSTRAINT ck_user_daily_quests_model_version
        CHECK (model_version IS NULL OR target_source = 'MODEL')
        NOT VALID;
