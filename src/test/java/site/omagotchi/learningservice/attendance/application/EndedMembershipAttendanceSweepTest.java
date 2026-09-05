package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("종료 소속 출결 정합성 스윕")
class EndedMembershipAttendanceSweepTest {

    private static final int BATCH_SIZE = 3;
    private static final Long NON_ENDED_MEMBERSHIP_ID = 11L;
    private static final Long ENDED_MEMBERSHIP_ID = 22L;
    private static final OffsetDateTime ENDED_AT = OffsetDateTime.parse("2026-09-04T09:00:00Z");

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private EndedMembershipAttendanceCleanup attendanceCleanup;

    private EndedMembershipAttendanceSweep sweep;

    @BeforeEach
    void setUp() {
        sweep = new EndedMembershipAttendanceSweep(
                attendanceRecordRepository,
                cohortMembershipQueryService,
                attendanceCleanup
        );
    }

    @Test
    @DisplayName("ENDED 소속의 출결만 정리하고 다른 상태는 건드리지 않는다")
    void cleansOnlyEndedMemberships() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(
                        target(1L, NON_ENDED_MEMBERSHIP_ID),
                        target(2L, ENDED_MEMBERSHIP_ID)
                ));
        given(cohortMembershipQueryService.findEndedMemberships(
                List.of(NON_ENDED_MEMBERSHIP_ID, ENDED_MEMBERSHIP_ID)))
                .willReturn(Map.of(ENDED_MEMBERSHIP_ID, ENDED_AT));
        given(attendanceCleanup.cleanUp(ENDED_MEMBERSHIP_ID)).willReturn(1);

        assertThat(sweep.sweep(BATCH_SIZE)).isEqualTo(1);

        verify(attendanceCleanup).cleanUp(ENDED_MEMBERSHIP_ID);
        verify(attendanceCleanup, never()).cleanUp(NON_ENDED_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("같은 소속에 미해결 출결이 여러 개여도 소속 정리는 한 번만 호출한다")
    void cleansEachMembershipOncePerRun() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(
                        target(1L, ENDED_MEMBERSHIP_ID),
                        target(2L, ENDED_MEMBERSHIP_ID)
                ));
        given(cohortMembershipQueryService.findEndedMemberships(
                List.of(ENDED_MEMBERSHIP_ID)))
                .willReturn(Map.of(ENDED_MEMBERSHIP_ID, ENDED_AT));
        given(attendanceCleanup.cleanUp(ENDED_MEMBERSHIP_ID)).willReturn(2);

        assertThat(sweep.sweep(BATCH_SIZE)).isEqualTo(2);

        verify(attendanceCleanup, times(1)).cleanUp(ENDED_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("한 소속 정리가 실패해도 나머지 소속을 계속 처리한다")
    void continuesAfterSingleMembershipFailure() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(
                        target(1L, 11L),
                        target(2L, 22L)
                ));
        given(cohortMembershipQueryService.findEndedMemberships(List.of(11L, 22L)))
                .willReturn(Map.of(11L, ENDED_AT, 22L, ENDED_AT));
        given(attendanceCleanup.cleanUp(11L))
                .willThrow(new IllegalStateException("일시적인 정리 실패"));
        given(attendanceCleanup.cleanUp(22L)).willReturn(1);

        assertThat(sweep.sweep(BATCH_SIZE)).isEqualTo(1);

        verify(attendanceCleanup).cleanUp(22L);
    }

    @Test
    @DisplayName("실패한 소속은 다음 실행에서 다시 발견해 처리한다")
    void retriesFailedMembershipOnNextRun() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(target(1L, ENDED_MEMBERSHIP_ID)));
        given(cohortMembershipQueryService.findEndedMemberships(
                List.of(ENDED_MEMBERSHIP_ID)))
                .willReturn(Map.of(ENDED_MEMBERSHIP_ID, ENDED_AT));
        given(attendanceCleanup.cleanUp(ENDED_MEMBERSHIP_ID))
                .willThrow(new IllegalStateException("첫 실행 실패"))
                .willReturn(1);

        assertThat(sweep.sweep(BATCH_SIZE)).isZero();
        assertThat(sweep.sweep(BATCH_SIZE)).isEqualTo(1);

        verify(attendanceCleanup, times(2)).cleanUp(ENDED_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("배치가 가득 차면 마지막 출결 ID 다음부터 계속 조회한다")
    void advancesAttendanceCursorAcrossBatches() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(
                        target(1L, 11L),
                        target(2L, 22L),
                        target(3L, 33L)
                ));
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(3L, BATCH_SIZE))
                .willReturn(List.of(target(4L, 44L)));
        given(cohortMembershipQueryService.findEndedMemberships(any()))
                .willReturn(Map.of());

        sweep.sweep(BATCH_SIZE);

        verify(attendanceRecordRepository).findEndCleanupTargetsAfter(0L, BATCH_SIZE);
        verify(attendanceRecordRepository).findEndCleanupTargetsAfter(3L, BATCH_SIZE);
        verify(cohortMembershipQueryService, times(2)).findEndedMemberships(any());
    }

    @Test
    @DisplayName("미해결 출결이 없으면 소속 상태도 조회하지 않는다")
    void stopsWithoutCandidates() {
        given(attendanceRecordRepository.findEndCleanupTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of());

        assertThat(sweep.sweep(BATCH_SIZE)).isZero();

        verifyNoInteractions(cohortMembershipQueryService, attendanceCleanup);
        verify(attendanceRecordRepository, times(1))
                .findEndCleanupTargetsAfter(anyLong(), anyInt());
    }

    private static AttendanceCleanupTarget target(Long attendanceId, Long membershipId) {
        return new AttendanceCleanupTarget(attendanceId, membershipId);
    }
}
