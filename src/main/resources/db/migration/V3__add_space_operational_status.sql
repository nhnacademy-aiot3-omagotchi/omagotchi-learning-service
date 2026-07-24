-- =========================================================
-- 공간 운영 상태 및 비활성 사유 추가
--
-- RM-10
-- 공간은 ACTIVE / INACTIVE 운영 상태를 가진다.
-- 새로 생성되는 공간의 기본 상태는 INACTIVE이다.
--
-- RM-11
-- 공간은 비활성 사유를 가질 수 있다.
-- 현재 비활성 사유의 필수 여부는 확정되지 않았으므로
-- NULL을 허용한다.
-- =========================================================


-- 1. 공간 운영 상태 컬럼 추가
--
-- 신규 공간은 기본적으로 INACTIVE 상태로 생성한다.
ALTER TABLE spaces
    ADD COLUMN operational_status VARCHAR(20)
        NOT NULL
        DEFAULT 'INACTIVE';


-- 2. 공간 비활성 사유 컬럼 추가
--
-- 비활성 사유의 필수 여부가 아직 확정되지 않았기 때문에
-- NULL을 허용한다.
ALTER TABLE spaces
    ADD COLUMN inactive_reason VARCHAR(255);


-- 3. 기존에 등록되어 있던 삭제되지 않은 공간은
-- 이미 운영 중인 공간으로 간주하여 ACTIVE로 변경한다.
UPDATE spaces
SET operational_status = 'ACTIVE'
WHERE deleted_at IS NULL;


-- 4. 허용된 운영 상태만 저장하도록 제약조건을 추가한다.
ALTER TABLE spaces
    ADD CONSTRAINT chk_spaces_operational_status
        CHECK (
            operational_status IN (
                                   'ACTIVE',
                                   'INACTIVE'
                )
            );


-- 5. 컬럼 설명
COMMENT ON COLUMN spaces.operational_status IS
    '공간 운영 상태. ACTIVE 또는 INACTIVE. 삭제 여부는 deleted_at으로 판단한다.';

COMMENT ON COLUMN spaces.inactive_reason IS
    '공간 비활성 사유. 현재는 NULL을 허용한다.';