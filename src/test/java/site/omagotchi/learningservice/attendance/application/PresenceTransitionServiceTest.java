package site.omagotchi.learningservice.attendance.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("체류 구간 전환 서비스")
class PresenceTransitionServiceTest {

    private static final Long ATTENDANCE_ID = 1L;
    private static final Long MEMBERSHIP_ID = 77L;
    private static final Long LAB_A_ID = 10L;
    private static final Long LAB_B_ID = 20L;
    private static final Long MEETING_ID = 30L;
    private static final Long STUDY_SPACE_ID = 40L;
    private static final Instant CHECKED_IN_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private PresenceIntervalRepository presenceIntervalRepository;

    @InjectMocks
    private PresenceTransitionService service;

    @Test
    @DisplayName("체크인 후 최초 실습실 선택은 열린 구간이 없어도 PRESENT 구간을 시작한다")
    void firstLabSelectionStartsPresentInterval() {
        Instant at = Instant.parse("2026-08-31T01:00:00Z");
        stubAttendance(attendance(false));
        when(presenceIntervalRepository
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID))
                .thenReturn(List.of());
        ArgumentCaptor<PresenceInterval> captor = ArgumentCaptor.forClass(PresenceInterval.class);

        service.moveLab(ATTENDANCE_ID, MEMBERSHIP_ID, LAB_A_ID, at);

        verify(presenceIntervalRepository).save(captor.capture());
        assertThat(captor.getValue().getAttendanceId()).isEqualTo(ATTENDANCE_ID);
        assertThat(captor.getValue().getState()).isEqualTo(PresenceState.PRESENT);
        assertThat(captor.getValue().getSpaceId()).isEqualTo(LAB_A_ID);
        assertThat(captor.getValue().getStartedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("같은 실습실의 PRESENT 시작 재요청은 새 행을 만들지 않는다")
    void treatsSameAttendanceStartAsIdempotent() {
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        service.moveLab(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                LAB_A_ID,
                Instant.parse("2026-08-31T01:05:00Z")
        );

        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("열린 구간이 두 개면 최신 구간을 고르지 않고 정합성 오류를 반환한다")
    void rejectsDuplicateOpenIntervals() {
        stubAttendance(attendance(false));
        when(presenceIntervalRepository
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID))
                .thenReturn(List.of(
                        presence(PresenceState.PRESENT, LAB_A_ID, "2026-08-31T01:00:00Z"),
                        presence(PresenceState.PRESENT, LAB_B_ID, "2026-08-31T01:01:00Z")
                ));

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_INTERVAL_INCONSISTENT,
                () -> service.moveLab(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID,
                        LAB_B_ID,
                        Instant.parse("2026-08-31T02:00:00Z")
                )
        );
        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("요청한 소속이 출결 기록의 소속과 다르면 체류 구간을 조회하지 않고 거절한다")
    void rejectsTransitionForDifferentMembership() {
        stubAttendance(attendance(false));

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_MEMBERSHIP_MISMATCH,
                () -> service.moveLab(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID + 1L,
                        LAB_B_ID,
                        Instant.parse("2026-08-31T02:00:00Z")
                )
        );

        verify(presenceIntervalRepository, never())
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID);
        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("체류 공간 ID가 없으면 입력 오류로 거절한다")
    void rejectsMissingSpaceIdAsInvalidInput() {
        stubAttendance(attendance(false));

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_INVALID_SPACE_ID,
                () -> service.moveLab(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID,
                        null,
                        Instant.parse("2026-08-31T01:00:00Z")
                )
        );

        assertThat(AttendanceErrorCode.PRESENCE_INVALID_SPACE_ID.type())
                .isEqualTo(ErrorType.INVALID_INPUT);
        verify(presenceIntervalRepository, never())
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID);
        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("실습실 이동은 이전 구간을 종료하고 같은 시각에 다음 PRESENT를 시작한다")
    void movesLabWithAdjacentIntervals() {
        Instant at = Instant.parse("2026-08-31T02:00:00Z");
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);
        ArgumentCaptor<PresenceInterval> captor = ArgumentCaptor.forClass(PresenceInterval.class);

        service.moveLab(ATTENDANCE_ID, MEMBERSHIP_ID, LAB_B_ID, at);

        verify(presenceIntervalRepository, times(2)).save(captor.capture());
        PresenceInterval next = captor.getAllValues().get(1);
        assertThat(current.getEndedAt()).isEqualTo(at);
        assertThat(next.getState()).isEqualTo(PresenceState.PRESENT);
        assertThat(next.getSpaceId()).isEqualTo(LAB_B_ID);
        assertThat(next.getStartedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("도서관 입장은 이전 재실 구간을 종료하고 STUDY 공간의 PRESENT를 시작한다")
    void movesToStudySpaceWithAdjacentIntervals() {
        Instant at = Instant.parse("2026-08-31T02:00:00Z");
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);
        ArgumentCaptor<PresenceInterval> captor = ArgumentCaptor.forClass(PresenceInterval.class);

        service.moveStudySpace(ATTENDANCE_ID, MEMBERSHIP_ID, STUDY_SPACE_ID, at);

        verify(presenceIntervalRepository, times(2)).save(captor.capture());
        PresenceInterval next = captor.getAllValues().get(1);
        assertThat(current.getEndedAt()).isEqualTo(at);
        assertThat(next.getState()).isEqualTo(PresenceState.PRESENT);
        assertThat(next.getSpaceId()).isEqualTo(STUDY_SPACE_ID);
        assertThat(next.getStartedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("회의실에 체류 중이면 실습실 이동을 거절한다")
    void rejectsLabMoveWhileInMeeting() {
        PresenceInterval current = presence(
                PresenceState.MEETING,
                MEETING_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_MEETING_EXIT_REQUIRED,
                () -> service.moveLab(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID,
                        LAB_B_ID,
                        Instant.parse("2026-08-31T02:00:00Z")
                )
        );

        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("회의 입실은 기존 비회의 구간을 종료하고 MEETING 구간을 시작한다")
    void entersMeetingFromNonMeetingInterval() {
        Instant at = Instant.parse("2026-08-31T02:00:00Z");
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);
        ArgumentCaptor<PresenceInterval> captor = ArgumentCaptor.forClass(PresenceInterval.class);

        service.enterMeeting(ATTENDANCE_ID, MEMBERSHIP_ID, MEETING_ID, at);

        verify(presenceIntervalRepository, times(2)).save(captor.capture());
        PresenceInterval next = captor.getAllValues().get(1);
        assertThat(current.getEndedAt()).isEqualTo(at);
        assertThat(next.getState()).isEqualTo(PresenceState.MEETING);
        assertThat(next.getSpaceId()).isEqualTo(MEETING_ID);
        assertThat(next.getStartedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("같은 회의실 입실 재요청은 새 구간을 만들지 않는다")
    void treatsSameMeetingEntryAsIdempotent() {
        PresenceInterval current = presence(
                PresenceState.MEETING,
                MEETING_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        service.enterMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                MEETING_ID,
                Instant.parse("2026-08-31T02:00:00Z")
        );

        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("공간이 없는 기존 PRESENT 구간에서는 회의실 입실을 거절한다")
    void rejectsMeetingEntryFromPresentIntervalWithoutSpace() {
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                null,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_STATE_MISMATCH,
                () -> service.enterMeeting(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID,
                        MEETING_ID,
                        Instant.parse("2026-08-31T02:00:00Z")
                )
        );

        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("도서관에서 시작한 회의의 이탈은 직전 도서관으로 PRESENT를 재개한다")
    void leavesMeetingAndReturnsToPreviousSpace() {
        Instant meetingStartedAt = Instant.parse("2026-08-31T02:00:00Z");
        Instant leaveAt = Instant.parse("2026-08-31T03:00:00Z");
        PresenceInterval previous = presence(
                PresenceState.PRESENT,
                STUDY_SPACE_ID,
                "2026-08-31T01:00:00Z"
        );
        previous.end(meetingStartedAt);
        PresenceInterval meeting = presence(
                PresenceState.MEETING,
                MEETING_ID,
                meetingStartedAt.toString()
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(meeting);
        when(presenceIntervalRepository
                .findFirstByAttendanceIdAndStateNotAndEndedAtOrderByStartedAtDescIdDesc(
                        ATTENDANCE_ID,
                        PresenceState.MEETING,
                        meetingStartedAt
                ))
                .thenReturn(Optional.of(previous));
        ArgumentCaptor<PresenceInterval> captor = ArgumentCaptor.forClass(PresenceInterval.class);

        service.leaveMeeting(ATTENDANCE_ID, MEMBERSHIP_ID, MEETING_ID, leaveAt);

        verify(presenceIntervalRepository, times(2)).save(captor.capture());
        PresenceInterval returned = captor.getAllValues().get(1);
        assertThat(meeting.getEndedAt()).isEqualTo(leaveAt);
        assertThat(returned.getState()).isEqualTo(PresenceState.PRESENT);
        assertThat(returned.getSpaceId()).isEqualTo(STUDY_SPACE_ID);
        assertThat(returned.getStartedAt()).isEqualTo(leaveAt);
    }

    @Test
    @DisplayName("이미 회의에서 복귀한 같은 이탈 재요청은 새 구간을 만들지 않는다")
    void treatsRepeatedMeetingLeaveAsIdempotent() {
        Instant leaveAt = Instant.parse("2026-08-31T03:00:00Z");
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                leaveAt.toString()
        );
        PresenceInterval closedMeeting = presence(
                PresenceState.MEETING,
                MEETING_ID,
                "2026-08-31T02:00:00Z"
        );
        closedMeeting.end(leaveAt);
        stubAttendance(attendance(false));
        stubOpenIntervals(current);
        when(presenceIntervalRepository
                .findFirstByAttendanceIdAndStateAndEndedAtOrderByStartedAtDescIdDesc(
                        ATTENDANCE_ID,
                        PresenceState.MEETING,
                        leaveAt
                ))
                .thenReturn(Optional.of(closedMeeting));

        service.leaveMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                MEETING_ID,
                Instant.parse("2026-08-31T03:01:00Z")
        );

        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("체크아웃된 출결에는 새 회의 구간을 만들지 않는다")
    void rejectsTransitionAfterCheckout() {
        stubAttendance(attendance(true));

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_TRANSITION_NOT_ALLOWED,
                () -> service.enterMeeting(
                        ATTENDANCE_ID,
                        MEMBERSHIP_ID,
                        MEETING_ID,
                        Instant.parse("2026-08-31T02:00:00Z")
                )
        );
        verify(presenceIntervalRepository, never())
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID);
    }

    @Test
    @DisplayName("체류 종료 시각이 시작보다 빠르면 공개된 시간 오류로 변환한다")
    void mapsInvalidEndTimeToBusinessError() {
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T02:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        assertBusinessError(
                AttendanceErrorCode.PRESENCE_INVALID_TIME,
                () -> service.closeAttendance(
                        ATTENDANCE_ID,
                        Instant.parse("2026-08-31T01:59:59Z")
                )
        );
        assertThat(AttendanceErrorCode.PRESENCE_INVALID_TIME.type())
                .isEqualTo(ErrorType.CONFLICT);
        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("체류 종료는 행을 삭제하지 않고 열린 구간의 endedAt만 채운다")
    void closesAttendanceWithoutDeletingInterval() {
        Instant at = Instant.parse("2026-08-31T04:00:00Z");
        PresenceInterval current = presence(
                PresenceState.PRESENT,
                LAB_A_ID,
                "2026-08-31T01:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(current);

        boolean closed = service.closeAttendance(ATTENDANCE_ID, at);

        assertThat(closed).isTrue();
        assertThat(current.getEndedAt()).isEqualTo(at);
        verify(presenceIntervalRepository).save(current);
        verify(presenceIntervalRepository, never()).delete(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("열린 체류 구간이 없는 출결 마감은 변경 없음으로 응답한다")
    void returnsFalseWithoutOpenInterval() {
        stubAttendance(attendance(false));
        when(presenceIntervalRepository
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID))
                .thenReturn(List.of());

        boolean closed = service.closeAttendance(
                ATTENDANCE_ID,
                Instant.parse("2026-08-31T04:00:00Z")
        );

        assertThat(closed).isFalse();
        verify(presenceIntervalRepository, never()).save(any(PresenceInterval.class));
    }

    @Test
    @DisplayName("소속 종료 회의 마감은 MEETING만 닫고 복귀 PRESENT를 만들지 않는다")
    void closesMeetingWithoutReturnForEndedMembership() {
        Instant at = Instant.parse("2026-08-31T04:00:00Z");
        PresenceInterval meeting = presence(
                PresenceState.MEETING,
                MEETING_ID,
                "2026-08-31T02:00:00Z"
        );
        stubAttendance(attendance(false));
        stubOpenIntervals(meeting);

        service.closeAnyMeetingWithoutReturn(ATTENDANCE_ID, MEMBERSHIP_ID, at);

        assertThat(meeting.getEndedAt()).isEqualTo(at);
        verify(presenceIntervalRepository).save(meeting);
        verify(presenceIntervalRepository, times(1)).save(any(PresenceInterval.class));
    }

    private AttendanceRecord attendance(boolean checkedOut) {
        AttendanceRecord record = AttendanceRecord.start(
                MEMBERSHIP_ID,
                LocalDate.of(2026, 8, 31)
        );
        ReflectionTestUtils.setField(record, "id", ATTENDANCE_ID);
        record.checkIn(CHECKED_IN_AT, AttendanceStatus.PRESENT, 0);
        if (checkedOut) {
            record.checkOut(
                    Instant.parse("2026-08-31T01:30:00Z"),
                    AttendanceStatus.PRESENT,
                    0
            );
        }
        return record;
    }

    private PresenceInterval presence(
            PresenceState state,
            Long spaceId,
            String startedAt
    ) {
        return PresenceInterval.start(
                ATTENDANCE_ID,
                state,
                spaceId,
                Instant.parse(startedAt)
        );
    }

    private void stubAttendance(AttendanceRecord attendance) {
        when(attendanceRecordRepository.findByIdForUpdate(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));
    }

    private void stubOpenIntervals(PresenceInterval interval) {
        when(presenceIntervalRepository
                .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(ATTENDANCE_ID))
                .thenReturn(List.of(interval));
    }

    private void assertBusinessError(
            AttendanceErrorCode expected,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expected));
    }
}
