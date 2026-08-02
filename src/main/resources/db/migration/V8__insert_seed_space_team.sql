-- 공간 시드
--
-- spaces.status 의 DEFAULT 는 INACTIVE 다 (RM-10). status 를 명시하지 않으면
-- 배포 직후 모든 공간이 점유·재실 신청을 받지 못하므로 전부 ACTIVE 를 지정한다.
--
-- cohort_id = NULL 인 시드 공간은 관리 주체 기수가 없어 삭제 주체도 없다 (RM-25).
-- 삭제 차단은 DB 제약이 아니라 삭제 API 의 책임이다.
-- LAB 은 미배정 상태로 시작하고 실제 배정은 실습실 배정 API 가 수행한다 (RM-24).
--
-- TODO(운영): 아래 목록은 예시다. 실제 원하는 시설 명칭·수량·정원으로 교체할 것.

INSERT INTO learning_service.spaces (name, space_type, cohort_id, capacity, status)
VALUES
    ('1실습실', 'LAB', NULL, 30, 'ACTIVE'),
    ('2실습실', 'LAB', NULL, 30, 'ACTIVE'),
    ('3실습실', 'LAB', NULL, 30, 'ACTIVE'),
    ('1회의실', 'MEETING', NULL, 6, 'ACTIVE'),
    ('2회의실', 'MEETING', NULL, 6, 'ACTIVE'),
    ('3회의실', 'MEETING', NULL, 4, 'ACTIVE'),
    ('4회의실', 'MEETING', NULL, 4, 'ACTIVE'),
    ('1독서실', 'STUDY', NULL, 20, 'ACTIVE'),
    ('2독서실', 'STUDY', NULL, 20, 'ACTIVE')
    ON CONFLICT DO NOTHING;

-- 위 함정을 배포 시점에 잡기 위한 안전망이다.
DO $$
DECLARE
    active_total INTEGER;
    active_meeting INTEGER;
BEGIN
    SELECT count(*) INTO active_total
    FROM learning_service.spaces
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

    SELECT count(*) INTO active_meeting
    FROM learning_service.spaces
    WHERE status = 'ACTIVE' AND deleted_at IS NULL AND space_type = 'MEETING';

    IF active_total = 0 THEN
        RAISE EXCEPTION '시드 검증 실패: 활성 공간이 0개입니다. spaces.status DEFAULT 가 INACTIVE 이므로 시드에서 ACTIVE 를 명시했는지 확인하세요.';
    END IF;

    IF active_meeting = 0 THEN
        RAISE EXCEPTION '시드 검증 실패: 활성 회의실이 0개입니다. 점유 기능이 동작할 수 없습니다.';
    END IF;
END
$$;