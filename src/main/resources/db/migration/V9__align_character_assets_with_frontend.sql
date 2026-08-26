-- 캐릭터 자산 컬럼을 추가한다.
-- 캐릭터 행 자체(name·description·asset_key)는 GamificationReferenceDataBootstrap 이 소유한다.
ALTER TABLE learning_service.game_characters
    ADD COLUMN asset_key VARCHAR(30);

ALTER TABLE learning_service.user_characters
    ADD COLUMN color_id VARCHAR(30) DEFAULT 'original';

UPDATE learning_service.user_characters
SET color_id = 'original'
WHERE color_id IS NULL;
