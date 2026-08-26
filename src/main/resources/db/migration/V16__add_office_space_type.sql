-- 센서가 설치된 곳 중에는 예약 대상이 아닌 구역이 있다(사무실).
--
-- 유형이 늘어도 점유 로직은 바뀌지 않는다 — SpaceAccessNativeQueryReader 가
-- (space_type = 'MEETING') 으로 불린을 만들어 넘기므로 OFFICE 는 자동으로
-- meetingRoom = false 가 되어 NOT_MEETING_ROOM 으로 거부된다.
-- SpaceAccessView 의 javadoc 이 명시한 설계 의도 그대로다.
--
-- 공간과 센서의 실제 데이터는 이 파일에 넣지 않는다. 그것은 이 설치 현장의
-- 인벤토리이지 제품 정의가 아니며, 관리 API 로 등록한다.
--
-- PostgreSQL 에는 CHECK 제약을 수정하는 구문이 없다. 같은 이름으로 지우고 다시 만든다.
-- DDL 이 트랜잭션이라 중간에 실패하면 DROP 까지 되돌아간다.
ALTER TABLE learning_service.spaces
    DROP CONSTRAINT ck_spaces_space_type;

ALTER TABLE learning_service.spaces
    ADD CONSTRAINT ck_spaces_space_type
        CHECK (space_type IN ('LAB', 'MEETING', 'STUDY', 'OFFICE'));
