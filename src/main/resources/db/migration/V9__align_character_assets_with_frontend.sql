ALTER TABLE learning_service.game_characters
    ADD COLUMN asset_key VARCHAR(30);

UPDATE learning_service.game_characters
SET asset_key = CASE code
    WHEN 'NIGHT_CLASS' THEN 'night'
    ELSE lower(code)
END;

UPDATE learning_service.game_characters
SET description = '늦은 시간에도 집중력을 붙잡는 야간형 오마고치입니다.'
WHERE code = 'NIGHT_CLASS';

INSERT INTO learning_service.game_characters (name, code, description, asset_key) VALUES
    ('공부쟁이', 'STUDY', '기본기가 탄탄한 학습형 오마고치입니다.', 'study'),
    ('디버깅이', 'DEBUG', '문제를 발견하면 끝까지 추적하는 오마고치입니다.', 'debug'),
    ('새싹이', 'SPROUT', '작은 출석에도 크게 반응하는 성장형 오마고치입니다.', 'sprout'),
    ('서버지킴이', 'SERVER', '꾸준한 루틴과 긴 집중에 어울리는 오마고치입니다.', 'server'),
    ('잼민이', 'KID', '공부에 리듬을 만들어 주는 에너지형 오마고치입니다.', 'kid'),
    ('카페인이', 'CAFFEINE', '짧고 강한 집중에 특화된 오마고치입니다.', 'caffeine'),
    ('커밋이', 'COMMIT', '기록과 회고를 좋아하는 습관형 오마고치입니다.', 'commit');

ALTER TABLE learning_service.user_characters
    ADD COLUMN color_id VARCHAR(30) DEFAULT 'original';

UPDATE learning_service.user_characters
SET color_id = 'original'
WHERE color_id IS NULL;
