package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.result.DailyMissingCheckOutTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.result.DailyAttendanceClosingPolicyView;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("일일 미퇴실 마감 배치")
class DailyMissingCheckOutBatchTest {

    private static final int BATCH_SIZE = 200;
    private static final Long SEOUL_MEMBERSHIP_ID = 10L;
    private static final Long NEW_YORK_MEMBERSHIP_ID = 20L;
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 5);
    private static final Instant SEOUL_DEADLINE = Instant.parse("2026-09-05T09:00:00Z");

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private CohortAttendancePolicyService attendancePolicyService;

    @Mock
    private MissingCheckOutFinalizer finalizer;

    private DailyMissingCheckOutBatch batch;

    @BeforeEach
    void setUp() {
        batch = batchAt(SEOUL_DEADLINE);
    }

    @Test
    @DisplayName("기수 timezone의 예정 종료 시각에 도달한 출결만 마감한다")
    void closesOnlyAttendancesWhoseLocalDeadlineHasArrived() {
        DailyMissingCheckOutTarget seoul = target(1L, SEOUL_MEMBERSHIP_ID);
        DailyMissingCheckOutTarget newYork = target(2L, NEW_YORK_MEMBERSHIP_ID);
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(seoul, newYork));
        given(attendancePolicyService.findActiveDailyClosingPolicies(
                List.of(SEOUL_MEMBERSHIP_ID, NEW_YORK_MEMBERSHIP_ID)))
                .willReturn(Map.of(
                        SEOUL_MEMBERSHIP_ID,
                        policy(SEOUL_MEMBERSHIP_ID, "Asia/Seoul"),
                        NEW_YORK_MEMBERSHIP_ID,
                        policy(NEW_YORK_MEMBERSHIP_ID, "America/New_York")
                ));
        given(finalizer.finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE))
                .willReturn(true);

        assertThat(batch.closeDueAttendances(BATCH_SIZE)).isEqualTo(1);

        verify(finalizer).finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE);
        verify(finalizer, never()).finalizeDaily(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(NEW_YORK_MEMBERSHIP_ID),
                any()
        );
    }

    @Test
    @DisplayName("예정 종료 1초 전에는 마감하지 않는다")
    void doesNotCloseBeforeScheduledEnd() {
        batch = batchAt(SEOUL_DEADLINE.minusSeconds(1));
        DailyMissingCheckOutTarget target = target(1L, SEOUL_MEMBERSHIP_ID);
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(target));
        given(attendancePolicyService.findActiveDailyClosingPolicies(
                List.of(SEOUL_MEMBERSHIP_ID)))
                .willReturn(Map.of(
                        SEOUL_MEMBERSHIP_ID,
                        policy(SEOUL_MEMBERSHIP_ID, "Asia/Seoul")
                ));

        assertThat(batch.closeDueAttendances(BATCH_SIZE)).isZero();

        verify(finalizer, never()).finalizeDaily(any(), any(), any());
    }

    @Test
    @DisplayName("서버가 늦게 재기동해도 발견 시각이 아닌 원래 마감 시각을 쓴다")
    void catchesUpUsingOriginalDeadline() {
        batch = batchAt(SEOUL_DEADLINE.plusSeconds(3600));
        DailyMissingCheckOutTarget target = target(1L, SEOUL_MEMBERSHIP_ID);
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(target));
        given(attendancePolicyService.findActiveDailyClosingPolicies(
                List.of(SEOUL_MEMBERSHIP_ID)))
                .willReturn(Map.of(
                        SEOUL_MEMBERSHIP_ID,
                        policy(SEOUL_MEMBERSHIP_ID, "Asia/Seoul")
                ));
        given(finalizer.finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE))
                .willReturn(true);

        assertThat(batch.closeDueAttendances(BATCH_SIZE)).isEqualTo(1);

        verify(finalizer).finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE);
    }

    @Test
    @DisplayName("회의 중이어서 건너뛴 대상은 다음 실행에서 다시 시도한다")
    void retriesTargetSkippedForOpenMeeting() {
        DailyMissingCheckOutTarget target = target(1L, SEOUL_MEMBERSHIP_ID);
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(0L, BATCH_SIZE))
                .willReturn(List.of(target));
        given(attendancePolicyService.findActiveDailyClosingPolicies(
                List.of(SEOUL_MEMBERSHIP_ID)))
                .willReturn(Map.of(
                        SEOUL_MEMBERSHIP_ID,
                        policy(SEOUL_MEMBERSHIP_ID, "Asia/Seoul")
                ));
        given(finalizer.finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE))
                .willReturn(false, true);

        assertThat(batch.closeDueAttendances(BATCH_SIZE)).isZero();
        assertThat(batch.closeDueAttendances(BATCH_SIZE)).isEqualTo(1);

        verify(finalizer, times(2))
                .finalizeDaily(1L, SEOUL_MEMBERSHIP_ID, SEOUL_DEADLINE);
    }

    @Test
    @DisplayName("커서를 전진해 현재 마감 시각 전의 행 뒤에 있는 과거 미처리분도 훑는다")
    void advancesCursorAcrossAllCandidates() {
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(0L, 2))
                .willReturn(List.of(
                        target(1L, SEOUL_MEMBERSHIP_ID),
                        target(2L, NEW_YORK_MEMBERSHIP_ID)
                ));
        given(attendanceRecordRepository.findDailyMissingCheckOutTargetsAfter(2L, 2))
                .willReturn(List.of());
        given(attendancePolicyService.findActiveDailyClosingPolicies(any()))
                .willReturn(Map.of());

        batch.closeDueAttendances(2);

        verify(attendanceRecordRepository).findDailyMissingCheckOutTargetsAfter(2L, 2);
    }

    private DailyMissingCheckOutBatch batchAt(Instant now) {
        return new DailyMissingCheckOutBatch(
                attendanceRecordRepository,
                attendancePolicyService,
                finalizer,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private DailyMissingCheckOutTarget target(Long attendanceId, Long membershipId) {
        return new DailyMissingCheckOutTarget(
                attendanceId,
                membershipId,
                ATTENDANCE_DATE
        );
    }

    private DailyAttendanceClosingPolicyView policy(Long membershipId, String timezone) {
        return new DailyAttendanceClosingPolicyView(
                membershipId,
                timezone,
                LocalTime.of(18, 0)
        );
    }
}
