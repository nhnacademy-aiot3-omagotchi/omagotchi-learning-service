-- V5(공간·팀)가 occupier/participant FK로 (id, user_id) 복합 참조를 쓰기 위한 선행 조건.
-- id가 이미 PK이므로 이 UNIQUE는 어떤 행도 새로 막지 않는다(순수 참조점).
ALTER TABLE learning_service.cohort_memberships
    ADD CONSTRAINT uq_cohort_memberships_id_user UNIQUE (id, user_id);