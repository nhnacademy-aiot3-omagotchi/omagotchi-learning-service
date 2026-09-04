-- 연동 중인 행끼리만 유일하다. 해제된 행은 인덱스에서 빠져 자리를 반납하므로, 같은 텔레그램
-- 계정을 다른 사용자가 다시 연동할 수 있다 (V23 주석 참고).
--
-- 조건이 telegram_chat_id가 아니라 disconnected_at인 것에 유의한다 — chat_id는 NOT NULL이라
-- 널이 될 수 없고, 활성 여부를 나타내는 것은 해제 시각의 부재다.
--
-- 기존 전체 유니크 제약(uq_telegram_user_links_*)을 지우기 전에 이 부분 유니크 인덱스부터
-- 만든다. 반대로 하면 — 제약을 먼저 지우고 이 CONCURRENTLY 인덱스를 나중에 만들면 — 그
-- 사이 구간엔 활성 행 중복을 막는 DB 제약이 전혀 없다. 그 창에서 쓰기가 들어오면 같은
-- telegram_chat_id·telegram_user_id·user_id로 활성 행이 두 개 생길 수 있고, 인덱스 생성이
-- 실패하면 그 상태가 그대로 굳는다. 지금 순서라면 기존 전체 유니크 제약이 이
-- 인덱스가 완성될 때까지 계속 활성 행의 유일성까지 포함해 지켜주므로 보호 공백이 없다.
--
-- 이름을 uq_telegram_user_links_*가 아니라 _active_*로 다르게 지은 것도 같은 이유다 — 그
-- 이름은 아직 기존 제약의 기반 인덱스가 쓰고 있어 지우기 전에는 재사용할 수 없다.
CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_active_chat
    ON learning_service.telegram_user_links (telegram_chat_id)
    WHERE disconnected_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_active_telegram_user
    ON learning_service.telegram_user_links (telegram_user_id)
    WHERE disconnected_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY uq_telegram_user_links_active_user
    ON learning_service.telegram_user_links (user_id)
    WHERE disconnected_at IS NULL;
