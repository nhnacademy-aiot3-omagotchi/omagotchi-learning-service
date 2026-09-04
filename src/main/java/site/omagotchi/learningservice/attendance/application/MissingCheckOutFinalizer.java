package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
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

    /**
     * 열린 체류 구간을 닫고 출결을 {@code MISSING_CHECK_OUT}으로 확정한다.
     *
     * <p>{@link PresenceTransitionService#closeAttendance}가 먼저 출결 행을 잠근다. 같은
     * Transaction 안에서 이어지는 {@code findById}는 그 관리 엔티티를 받아 상태를 바꾼다.
     * 둘 중 하나라도 실패하면 체류와 출결 변경을 함께 롤백한다.</p>
     *
     * @return 체류 구간 또는 출결 상태 중 하나라도 이번 호출로 변경됐으면 {@code true}
     */
    @Transactional
    public boolean finalizeOne(Long attendanceId, Instant endedAt) {
        boolean presenceClosed = presenceTransitionService.closeAttendance(attendanceId, endedAt);
        AttendanceRecord attendance = attendanceRecordRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND
                ));
        boolean attendanceFinalized = attendance.markMissingCheckOut();
        return presenceClosed || attendanceFinalized;
    }
}
