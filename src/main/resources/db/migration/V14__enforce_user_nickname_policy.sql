ALTER TABLE learning_service.user_characters
    ADD CONSTRAINT ck_user_characters_nickname_policy
        CHECK (
            nickname = BTRIM(nickname)
            AND char_length(nickname) BETWEEN 2 AND 12
            AND nickname ~ '^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]+$'
        ) NOT VALID;

ALTER TABLE learning_service.user_characters
    VALIDATE CONSTRAINT ck_user_characters_nickname_policy;

ALTER TABLE learning_service.user_characters
    DROP CONSTRAINT ck_user_characters_nickname;

ALTER TABLE learning_service.user_characters
    RENAME CONSTRAINT ck_user_characters_nickname_policy
        TO ck_user_characters_nickname;
