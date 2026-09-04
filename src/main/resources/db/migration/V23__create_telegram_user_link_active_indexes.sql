-- 연동 중인 행끼리만 유일하다. 해제된 행은 인덱스에서 빠져 자리를 반납하므로, 같은 텔레그램
-- 계정을 다른 사용자가 다시 연동할 수 있다 (V22 주석 참고).
--
-- 조건이 telegram_chat_id가 아니라 disconnected_at인 것에 유의한다 — chat_id는 NOT NULL이라
-- 널이 될 수 없고, 활성 여부를 나타내는 것은 해제 시각의 부재다.

CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_chat
    ON learning_service.telegram_user_links (telegram_chat_id)
    WHERE disconnected_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_telegram_user
    ON learning_service.telegram_user_links (telegram_user_id)
    WHERE disconnected_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_user
    ON learning_service.telegram_user_links (user_id)
    WHERE disconnected_at IS NULL;
