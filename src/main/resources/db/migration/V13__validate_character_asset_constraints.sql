ALTER TABLE learning_service.game_characters
    ADD CONSTRAINT ck_game_characters_asset_key_not_null
        CHECK (asset_key IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_game_characters_asset_key
        CHECK (asset_key ~ '^[a-z0-9_]+$') NOT VALID;

ALTER TABLE learning_service.game_characters
    VALIDATE CONSTRAINT ck_game_characters_asset_key_not_null;

ALTER TABLE learning_service.game_characters
    VALIDATE CONSTRAINT ck_game_characters_asset_key;

ALTER TABLE learning_service.game_characters
    ALTER COLUMN asset_key SET NOT NULL,
    ADD CONSTRAINT uq_game_characters_asset_key
        UNIQUE USING INDEX ux_game_characters_asset_key,
    DROP CONSTRAINT ck_game_characters_asset_key_not_null;

ALTER TABLE learning_service.user_characters
    ADD CONSTRAINT ck_user_characters_color_id_not_null
        CHECK (color_id IS NOT NULL) NOT VALID,
    ADD CONSTRAINT ck_user_characters_color_id CHECK (
        color_id IN (
            'original',
            'pistachio',
            'cyan',
            'cream_can',
            'light_coral',
            'light_purple',
            'white',
            'dark_gray'
        )
    ) NOT VALID;

ALTER TABLE learning_service.user_characters
    VALIDATE CONSTRAINT ck_user_characters_color_id_not_null;

ALTER TABLE learning_service.user_characters
    VALIDATE CONSTRAINT ck_user_characters_color_id;

ALTER TABLE learning_service.user_characters
    ALTER COLUMN color_id SET NOT NULL,
    DROP CONSTRAINT ck_user_characters_color_id_not_null;
