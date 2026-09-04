package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 끝난 소속에 남은 미퇴실 출결과 체류 구간을 정리한다.
 *
 * <p>회의 중 체류는 점유·참여 정리가 먼저 소유한다. 열린 {@code MEETING}이 있으면
 * 아무것도 건드리지 않아 비동기 종료 경로의 순서 역전으로 점유 정리가 실패하는 일을
 * 막는다.</p>
 *
 * <p>이 Class의 루프에는 Transaction을 두지 않는다. 건별로
 * {@link MissingCheckOutFinalizer}의 Spring Proxy를 거쳐 독립 Transaction을 열어 한 건의
 * 실패가 나머지 출결을 막지 않게 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndedMembershipAttendanceCleanup {

    private final PresenceIntervalRepository presenceIntervalRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MissingCheckOutFinalizer missingCheckOutFinalizer;

    /**
     * @param membershipId 끝난 소속
     * @param endedAt      소속 종료 시각
     * @return 이번 호출로 체류 또는 출결 상태를 변경한 출결 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int cleanUp(Long membershipId, OffsetDateTime endedAt) {
        if (presenceIntervalRepository.existsOpenMeetingByMembershipId(membershipId)) {
            return 0;
        }

        List<AttendanceCleanupTarget> targets = attendanceRecordRepository
                .findEndCleanupTargetsByCohortMembershipId(membershipId);
        int cleaned = 0;
        for (AttendanceCleanupTarget target : targets) {
            try {
                if (missingCheckOutFinalizer.finalizeOne(
                        target.attendanceId(),
                        endedAt.toInstant()
                )) {
                    cleaned++;
                }
            } catch (Exception exception) {
                log.error("소속 종료 출결 정리에 실패했습니다. attendanceId={}, membershipId={}",
                        target.attendanceId(), membershipId, exception);
            }
        }
        return cleaned;
    }
}
