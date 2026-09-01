package site.omagotchi.learningservice.attendance.application.port;

/**
 * 출결 명령이 선택한 실습실을 최종 승인받는 동기 경계.
 *
 * <p>구현은 공간 기능에 있으며, 호출 트랜잭션 안에서 대상 공간 행을 잠근 뒤
 * LAB·활성·기수 배정·정원을 다시 확인한다. 포트를 출결 쪽이 소유하는 이유는
 * {@code space -> attendance} 기존 의존을 거슬러 Feature 순환 의존을 만들지 않기 위해서다.</p>
 */
public interface AttendanceLabAccessPort {

    /**
     * <p>구현은 반환할 때까지 대상 공간 행의 쓰기 잠금을 획득해, 이 검증과 이어지는
     * 체류 전환 사이에 공간이 비활성화되거나 배정 해제되지 않도록 보장해야 한다.</p>
     *
     * @param cohortId    출결이 속한 기수
     * @param attendanceId 기존 출결이면 식별자, 새 체크인이면 {@code null}
     * @param spaceId     선택한 실습실
     */
    void requireSelectableLab(Long cohortId, Long attendanceId, Long spaceId);
}
