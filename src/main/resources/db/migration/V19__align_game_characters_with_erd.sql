ALTER TABLE learning_service.game_characters
    DROP CONSTRAINT ck_game_characters_name;

ALTER TABLE learning_service.game_characters
    ADD COLUMN code VARCHAR(30),
    ADD COLUMN description VARCHAR(255),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE learning_service.game_characters game_character
SET
    code = CASE
        WHEN game_character.id = 1 THEN 'NIGHT_CLASS'
        ELSE 'CHARACTER_' || game_character.id
    END
WHERE game_character.code IS NULL;

ALTER TABLE learning_service.game_characters
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE learning_service.game_characters
    ALTER COLUMN name TYPE VARCHAR(50),
    ADD CONSTRAINT uq_game_characters_code UNIQUE (code),
    ADD CONSTRAINT ck_game_characters_code
        CHECK (code = upper(btrim(code)) AND char_length(code) BETWEEN 1 AND 30),
    ADD CONSTRAINT ck_game_characters_name
        CHECK (name = btrim(name) AND char_length(name) BETWEEN 1 AND 50),
    ADD CONSTRAINT ck_game_characters_description
        CHECK (description IS NULL OR char_length(description) <= 255);
