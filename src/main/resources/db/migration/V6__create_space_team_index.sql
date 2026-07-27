-- 공간·회의실·팀 도메인 부분 유니크·함수 인덱스 (ERD v3 3장)
--
-- WHERE 절이 붙는 유니크는 테이블 제약으로 표현할 수 없어 전부 인덱스로 만든다.
-- 따라서 애플리케이션에서 잡히는 예외는 제약 위반이 아니라 인덱스 위반이며,
-- 409 변환 시 인덱스명으로 분기해야 한다.

CREATE UNIQUE INDEX uq_spaces_active_name
    ON learning_service.spaces (LOWER(BTRIM(name)))
    WHERE deleted_at IS NULL;

-- 유니크가 아닌 것은 의도다 — 한 기수에 실습실을 여러 개 배정할 수 있다 (RM-26).
-- 그 대가로 동시 배정 방지는 spaces 행 락이 유일한 방어선이다.
CREATE INDEX ix_spaces_lab_cohort
    ON learning_service.spaces (cohort_id)
    WHERE space_type = 'LAB' AND deleted_at IS NULL;

-- 해체된 팀은 제외한다 — 동명 재생성을 허용해야 하므로 (GR-19).
CREATE UNIQUE INDEX uq_teams_active_name
    ON learning_service.teams (cohort_id, LOWER(BTRIM(name)))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_team_members_membership
    ON learning_service.team_members (cohort_membership_id);

-- 유니크 자체는 위 인덱스에 포함되지만 team_id 선두라 팀원 목록·정원 카운트
-- 조회 경로를 겸한다. 잉여로 보여도 삭제 금지.
CREATE UNIQUE INDEX uq_team_members_team_membership
    ON learning_service.team_members (team_id, cohort_membership_id);

-- 팀당 MASTER 최대 1명. 최소 1명은 DB로 표현할 수 없어 트랜잭션 책임이다 (GR-12).
CREATE UNIQUE INDEX uq_team_members_one_master
    ON learning_service.team_members (team_id)
    WHERE role = 'MASTER';

CREATE UNIQUE INDEX uq_room_occupancies_one_active_per_space
    ON learning_service.room_occupancies (space_id)
    WHERE status = 'ACTIVE';

-- 멤버십이 아니라 계정 기준이다 — 다기수 담당자도 방 2개를 잡을 수 없다 (MR-10).
CREATE UNIQUE INDEX uq_room_occupancies_one_active_per_user
    ON learning_service.room_occupancies (occupier_user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_room_occupancies_expiration
    ON learning_service.room_occupancies (expires_at)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_occupancy_participants_pair
    ON learning_service.occupancy_participants (occupancy_id, user_id);

CREATE UNIQUE INDEX uq_occupancy_participants_one_active
    ON learning_service.occupancy_participants (user_id)
    WHERE left_at IS NULL;

CREATE UNIQUE INDEX uq_vacancy_alerts_waiting
    ON learning_service.vacancy_alerts (space_id, cohort_membership_id)
    WHERE notified_at IS NULL;