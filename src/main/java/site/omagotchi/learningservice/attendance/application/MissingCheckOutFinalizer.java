package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCloseResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.result.EndedMembershipLockView;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Instant;

/**
 * 미퇴실 출결 한 건의 상태와 체류 구간을 원자적으로 마감한다.
 *
 * <p>이 Class가 건별 Transaction을 소유한다. 상위의 멤버십·기수 루프에서 한 건이
 * 실패해도 이미 성공한 다른 출결을 되돌리지 않기 위한 경계다.</p>
 */
@Service
@RequiredArgsConstructor
public class MissingCheckOutFinalizer {

    private final PresenceTransitionService presenceTransitionService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CohortLockService cohortLockService;

    /**
     * 열린 체류 구간을 닫고 출결을 {@code MISSING_CHECK_OUT}으로 확정한다.
     *
     * <p>소속 행을 먼저 잠그고, {@link PresenceTransitionService#closeAttendanceUnlessMeeting}
     * 이 출결 행을 잠근 뒤 열린 MEETING을 재확인한다. 점유 writer도 소속 → 출결 순서로
     * 잠그므로 검사와 확정 사이에 회의 입실이 끼어들 수 없다. 같은 Transaction 안에서
     * 이어지는 {@code findById}는 잠긴 관리 엔티티를 받아 상태를 바꾼다.</p>
     *
     * <p><b>ENDED 소속만 대상으로 삼는 것이 다른 종료 정리 단계와 다른 점이다.</b> 팀·알림·점유
     * 정리는 상태를 가리지 않는데(활성으로 좁히면 이미 ENDED라 대상을 못 찾기 때문), 여기는
     * 반대로 <b>살아 있는 소속의 출결을 실수로 마감하지 않기 위해</b> ENDED를 요구한다 —
     * 미퇴실 확정은 학생의 그날 출결 상태를 바꾸는 연산이라 잘못 돌면 되돌릴 사람이 없다.
     * 기수 종료는 상태 전이와 같은 Transaction에서, 개별 소속 종료는 그 커밋 뒤에 훅이 도므로
     * 두 경로 모두 이 시점에는 ENDED다. <b>대가는 소속 일괄 종료가 실패하면 출결 정리도 조용히
     * 건너뛴다는 것이다</b> — 그 경우 로그 없이 0건으로 끝난다.</p>
     *
     * <p>체류 종료 시각도 잠긴 소속의 {@code endedAt}을 사용한다. 호출 경로가 전달한
     * 이벤트 시각이나 스윕 발견 시각보다 DB에 보존된 실제 종료 시각이 정본이며, 이 값은
     * ENDED 상태의 DB 제약으로 null이 될 수 없다.</p>
     *
     * @return 체류 구간 또는 출결 상태 중 하나라도 이번 호출로 변경됐으면 {@code true}
     */
    @Transactional
    public boolean finalizeOne(Long attendanceId, Long membershipId) {
        EndedMembershipLockView endedMembership = cohortLockService
                .lockEndedMembership(membershipId)
                .orElse(null);
        if (endedMembership == null) {
            return false;
        }

        return finalizeLockedEndedMembership(
                attendanceId,
                membershipId,
                endedMembership.endedAt().toInstant()
        );
    }

    /**
     * ACTIVE 소속의 일일 미퇴실을 정책 마감 시각으로 확정한다.
     *
     * <p>소속 종료 전용인 {@link #finalizeOne(Long, Long)}과 달리 ACTIVE 소속을
     * 잠근다. 점유·공간 전환도 같은 소속 행을 먼저 잠그므로 출결 마감과
     * 새 MEETING 입실이 검사 사이에 교차하지 않는다.</p>
     */
    @Transactional
    public boolean finalizeDaily(
            Long attendanceId,
            Long membershipId,
            Instant deadline
    ) {
        if (cohortLockService.lockActiveMembership(membershipId).isEmpty()) {
            return false;
        }

        AttendanceCloseResult closeResult = presenceTransitionService
                .closeAttendanceAtDailyDeadline(attendanceId, membershipId, deadline);
        return finalizeAttendance(attendanceId, closeResult);
    }

    private boolean finalizeLockedEndedMembership(
            Long attendanceId,
            Long membershipId,
            Instant endedAt
    ) {
        AttendanceCloseResult closeResult = presenceTransitionService
                .closeAttendanceUnlessMeeting(attendanceId, membershipId, endedAt);
        return finalizeAttendance(attendanceId, closeResult);
    }

    private boolean finalizeAttendance(
            Long attendanceId,
            AttendanceCloseResult closeResult
    ) {
        if (closeResult == AttendanceCloseResult.MEETING_OPEN) {
            return false;
        }

        AttendanceRecord attendance = attendanceRecordRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND
                ));
        boolean attendanceFinalized = attendance.markMissingCheckOut();
        return closeResult == AttendanceCloseResult.CLOSED || attendanceFinalized;
    }
}
