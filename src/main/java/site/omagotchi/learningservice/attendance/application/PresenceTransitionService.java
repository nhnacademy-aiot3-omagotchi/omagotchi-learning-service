package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 출결에 속한 체류 구간을 생성·종료하는 단일 전환 경계.
 *
 * <p>DB 스키마에 열린 구간 고유 제약을 추가하지 않으므로 모든 명령은 먼저
 * {@code attendance_records} 행을 잠근다. 이전 구간은 삭제하지 않고 {@code ended_at}만
 * 채우며, 다음 구간은 같은 시각에 시작한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PresenceTransitionService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PresenceIntervalRepository presenceIntervalRepository;

    public void startAttendance(Long attendanceId, Long labSpaceId, Instant at) {
        AttendanceRecord attendance = lockAttendance(attendanceId);
        ensureOpenAttendance(attendance);
        requireSpaceId(labSpaceId);
        Instant transitionAt = requireTime(at);

        List<PresenceInterval> openIntervals = findOpenIntervals(attendanceId);
        if (openIntervals.isEmpty()) {
            presenceIntervalRepository.save(PresenceInterval.start(
                    attendanceId,
                    PresenceState.PRESENT,
                    labSpaceId,
                    transitionAt
            ));
            return;
        }

        PresenceInterval current = requireSingle(openIntervals);
        if (isSame(current, PresenceState.PRESENT, labSpaceId)) {
            return;
        }
        throw new BusinessException(AttendanceErrorCode.PRESENCE_STATE_MISMATCH);
    }

    public void moveLab(
            Long attendanceId,
            Long expectedMembershipId,
            Long nextLabSpaceId,
            Instant at
    ) {
        AttendanceRecord attendance = lockAttendance(attendanceId);
        ensureMembership(attendance, expectedMembershipId);
        ensureOpenAttendance(attendance);
        requireSpaceId(nextLabSpaceId);
        Instant transitionAt = requireTime(at);

        PresenceInterval current = requireCurrent(attendanceId);
        if (isSame(current, PresenceState.PRESENT, nextLabSpaceId)) {
            return;
        }
        if (current.getState() != PresenceState.PRESENT) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_STATE_MISMATCH);
        }

        transition(
                current,
                PresenceState.PRESENT,
                nextLabSpaceId,
                transitionAt
        );
    }

    public void enterMeeting(
            Long attendanceId,
            Long expectedMembershipId,
            Long meetingSpaceId,
            Instant at
    ) {
        AttendanceRecord attendance = lockAttendance(attendanceId);
        ensureMembership(attendance, expectedMembershipId);
        ensureOpenAttendance(attendance);
        requireSpaceId(meetingSpaceId);
        Instant transitionAt = requireTime(at);

        PresenceInterval current = requireCurrent(attendanceId);
        if (isSame(current, PresenceState.MEETING, meetingSpaceId)) {
            return;
        }
        if (current.getState() == PresenceState.MEETING || current.getSpaceId() == null) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_STATE_MISMATCH);
        }

        transition(
                current,
                PresenceState.MEETING,
                meetingSpaceId,
                transitionAt
        );
    }

    public void leaveMeeting(
            Long attendanceId,
            Long expectedMembershipId,
            Long meetingSpaceId,
            Instant at
    ) {
        AttendanceRecord attendance = lockAttendance(attendanceId);
        ensureMembership(attendance, expectedMembershipId);
        ensureOpenAttendance(attendance);
        requireSpaceId(meetingSpaceId);
        Instant transitionAt = requireTime(at);

        PresenceInterval current = requireCurrent(attendanceId);
        if (current.getState() != PresenceState.MEETING) {
            if (isIdempotentMeetingLeave(attendanceId, current, meetingSpaceId)) {
                return;
            }
            throw new BusinessException(AttendanceErrorCode.PRESENCE_STATE_MISMATCH);
        }
        if (!Objects.equals(current.getSpaceId(), meetingSpaceId)) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_STATE_MISMATCH);
        }

        PresenceInterval returnInterval = presenceIntervalRepository
                .findFirstByAttendanceIdAndStateNotAndEndedAtOrderByStartedAtDescIdDesc(
                        attendanceId,
                        PresenceState.MEETING,
                        current.getStartedAt()
                )
                .filter(interval -> interval.getSpaceId() != null)
                .orElseThrow(() -> new BusinessException(
                        AttendanceErrorCode.PRESENCE_RETURN_SPACE_NOT_FOUND
                ));

        transition(
                current,
                PresenceState.PRESENT,
                returnInterval.getSpaceId(),
                transitionAt
        );
    }

    public void closeAttendance(Long attendanceId, Instant at) {
        lockAttendance(attendanceId);
        Instant transitionAt = requireTime(at);
        List<PresenceInterval> openIntervals = findOpenIntervals(attendanceId);
        if (openIntervals.isEmpty()) {
            return;
        }

        end(requireSingle(openIntervals), transitionAt);
    }

    private AttendanceRecord lockAttendance(Long attendanceId) {
        if (attendanceId == null || attendanceId <= 0L) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND);
        }
        return attendanceRecordRepository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND
                ));
    }

    private List<PresenceInterval> findOpenIntervals(Long attendanceId) {
        return presenceIntervalRepository
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(attendanceId);
    }

    private PresenceInterval requireCurrent(Long attendanceId) {
        List<PresenceInterval> openIntervals = findOpenIntervals(attendanceId);
        if (openIntervals.isEmpty()) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_ACTIVE_INTERVAL_REQUIRED);
        }
        return requireSingle(openIntervals);
    }

    private PresenceInterval requireSingle(List<PresenceInterval> openIntervals) {
        if (openIntervals.size() > 1) {
            throw new BusinessException(
                    AttendanceErrorCode.PRESENCE_INTERVAL_INCONSISTENT
            );
        }
        return openIntervals.getFirst();
    }

    private void ensureOpenAttendance(AttendanceRecord attendance) {
        if (attendance.getCheckedInAt() == null || attendance.getCheckedOutAt() != null) {
            throw new BusinessException(
                    AttendanceErrorCode.PRESENCE_TRANSITION_NOT_ALLOWED
            );
        }
    }

    private void ensureMembership(
            AttendanceRecord attendance,
            Long expectedMembershipId
    ) {
        if (!Objects.equals(
                attendance.getCohortMembershipId(),
                expectedMembershipId
        )) {
            throw new BusinessException(
                    AttendanceErrorCode.PRESENCE_MEMBERSHIP_MISMATCH
            );
        }
    }

    private void transition(
            PresenceInterval current,
            PresenceState nextState,
            Long nextSpaceId,
            Instant at
    ) {
        end(current, at);
        presenceIntervalRepository.save(PresenceInterval.start(
                current.getAttendanceId(),
                nextState,
                nextSpaceId,
                at
        ));
    }

    private void end(PresenceInterval current, Instant at) {
        try {
            current.end(at);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    AttendanceErrorCode.PRESENCE_INVALID_TIME,
                    exception
            );
        }
        presenceIntervalRepository.save(current);
    }

    private boolean isIdempotentMeetingLeave(
            Long attendanceId,
            PresenceInterval current,
            Long meetingSpaceId
    ) {
        if (current.getState() != PresenceState.PRESENT) {
            return false;
        }
        return presenceIntervalRepository
                .findFirstByAttendanceIdAndStateAndEndedAtOrderByStartedAtDescIdDesc(
                        attendanceId,
                        PresenceState.MEETING,
                        current.getStartedAt()
                )
                .filter(meeting -> Objects.equals(meeting.getSpaceId(), meetingSpaceId))
                .isPresent();
    }

    private boolean isSame(
            PresenceInterval interval,
            PresenceState state,
            Long spaceId
    ) {
        return interval.getState() == state
                && Objects.equals(interval.getSpaceId(), spaceId);
    }

    private Long requireSpaceId(Long spaceId) {
        if (spaceId == null || spaceId <= 0L) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_INVALID_SPACE_ID);
        }
        return spaceId;
    }

    private Instant requireTime(Instant at) {
        if (at == null) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_INVALID_TIME);
        }
        return at;
    }
}
