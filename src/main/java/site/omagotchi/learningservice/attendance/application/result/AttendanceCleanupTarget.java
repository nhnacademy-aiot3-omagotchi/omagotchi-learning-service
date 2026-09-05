package site.omagotchi.learningservice.attendance.application.result;

/**
 * 종료 정리가 필요한 출결과 그 소속의 최소 참조.
 *
 * @param attendanceId       잠금·마감할 출결 기록
 * @param cohortMembershipId 대상 소속
 */
public record AttendanceCleanupTarget(
        Long attendanceId,
        Long cohortMembershipId
) {
}
