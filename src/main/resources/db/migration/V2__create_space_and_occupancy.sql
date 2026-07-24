-- 1. spaces 테이블 생성
CREATE TABLE spaces (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(50) NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        capacity INTEGER NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        deleted_at TIMESTAMPTZ
);

-- 💡 삭제되지 않은 공간에 대해서만 이름 중복을 막는 부분 유니크 인덱스
CREATE UNIQUE INDEX uq_spaces_name_active
    ON spaces (name)
    WHERE deleted_at IS NULL;

-- 2. room_occupancies 테이블 생성
CREATE TABLE room_occupancies (
                                  id BIGSERIAL PRIMARY KEY,
                                  space_id BIGINT NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  started_at TIMESTAMPTZ NOT NULL,
                                  expires_at TIMESTAMPTZ NOT NULL,
                                  ended_at TIMESTAMPTZ
);

-- 💡 한 공간에 활성화된 점유가 중복으로 생기지 않도록 막는 부분 유니크 인덱스
CREATE UNIQUE INDEX uq_room_occupancies_active_space
    ON room_occupancies (space_id)
    WHERE status = 'ACTIVE' AND ended_at IS NULL;