CREATE UNIQUE INDEX CONCURRENTLY ux_game_characters_asset_key
    ON learning_service.game_characters (asset_key);
