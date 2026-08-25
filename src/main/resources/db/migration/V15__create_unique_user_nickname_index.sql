CREATE UNIQUE INDEX CONCURRENTLY ux_user_characters_representative_nickname
    ON learning_service.user_characters (LOWER(nickname))
    WHERE is_representative;
