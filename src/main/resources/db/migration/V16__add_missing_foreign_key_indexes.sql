CREATE INDEX ix_user_characters_game_character
    ON learning_service.user_characters (game_character_id);

CREATE INDEX ix_user_daily_quests_template
    ON learning_service.user_daily_quests (template_id);

CREATE INDEX ix_advancement_histories_xp_transaction
    ON learning_service.advancement_histories (xp_transaction_id);

CREATE INDEX ix_ranking_snapshot_entries_user_character
    ON learning_service.ranking_snapshot_entries (user_character_id);
