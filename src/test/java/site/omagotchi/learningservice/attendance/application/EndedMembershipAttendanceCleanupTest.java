package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("종료 소속 출결 정리")
class EndedMembershipAttendanceCleanupTest {

    private static final Long MEMBERSHIP_ID = 20L;
    private static final OffsetDateTime ENDED_AT = OffsetDateTime.parse("2026-09-04T18:00:00+09:00");

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private MissingCheckOutFinalizer missingCheckOutFinalizer;

    @InjectMocks
    private EndedMembershipAttendanceCleanup cleanup;

    @Test
    @DisplayName("정리 대상이 없으면 0건을 반환한다")
    void returnsZeroWithoutTargets() {
        when(attendanceRecordRepository
                .findEndCleanupTargetsByCohortMembershipId(MEMBERSHIP_ID))
                .thenReturn(List.of());

        assertThat(cleanup.cleanUp(MEMBERSHIP_ID, ENDED_AT)).isZero();
        verifyNoInteractions(missingCheckOutFinalizer);
    }

    @Test
    @DisplayName("여러 날짜의 미퇴실 출결을 ID 순서대로 마감한다")
    void finalizesAllTargetsInOrder() {
        AttendanceCleanupTarget first = new AttendanceCleanupTarget(101L, MEMBERSHIP_ID);
        AttendanceCleanupTarget second = new AttendanceCleanupTarget(102L, MEMBERSHIP_ID);
        when(attendanceRecordRepository
                .findEndCleanupTargetsByCohortMembershipId(MEMBERSHIP_ID))
                .thenReturn(List.of(first, second));
        when(missingCheckOutFinalizer.finalizeOne(101L, MEMBERSHIP_ID, ENDED_AT.toInstant()))
                .thenReturn(true);
        when(missingCheckOutFinalizer.finalizeOne(102L, MEMBERSHIP_ID, ENDED_AT.toInstant()))
                .thenReturn(true);

        assertThat(cleanup.cleanUp(MEMBERSHIP_ID, ENDED_AT)).isEqualTo(2);

        var order = inOrder(missingCheckOutFinalizer);
        order.verify(missingCheckOutFinalizer)
                .finalizeOne(101L, MEMBERSHIP_ID, ENDED_AT.toInstant());
        order.verify(missingCheckOutFinalizer)
                .finalizeOne(102L, MEMBERSHIP_ID, ENDED_AT.toInstant());
    }

    @Test
    @DisplayName("한 출결의 실패가 다음 출결 마감을 막지 않는다")
    void continuesAfterOneTargetFails() {
        AttendanceCleanupTarget first = new AttendanceCleanupTarget(101L, MEMBERSHIP_ID);
        AttendanceCleanupTarget second = new AttendanceCleanupTarget(102L, MEMBERSHIP_ID);
        when(attendanceRecordRepository
                .findEndCleanupTargetsByCohortMembershipId(MEMBERSHIP_ID))
                .thenReturn(List.of(first, second));
        when(missingCheckOutFinalizer.finalizeOne(101L, MEMBERSHIP_ID, ENDED_AT.toInstant()))
                .thenThrow(new IllegalStateException("첫 출결 실패"));
        when(missingCheckOutFinalizer.finalizeOne(102L, MEMBERSHIP_ID, ENDED_AT.toInstant()))
                .thenReturn(true);

        assertThat(cleanup.cleanUp(MEMBERSHIP_ID, ENDED_AT)).isEqualTo(1);

        verify(missingCheckOutFinalizer)
                .finalizeOne(102L, MEMBERSHIP_ID, ENDED_AT.toInstant());
    }

    @Test
    @DisplayName("Finalizer가 이미 처리된 대상으로 판정하면 정리 건수에 포함하지 않는다")
    void countsOnlyChangedTargets() {
        AttendanceCleanupTarget target = new AttendanceCleanupTarget(101L, MEMBERSHIP_ID);
        when(attendanceRecordRepository
                .findEndCleanupTargetsByCohortMembershipId(MEMBERSHIP_ID))
                .thenReturn(List.of(target));
        when(missingCheckOutFinalizer.finalizeOne(101L, MEMBERSHIP_ID, ENDED_AT.toInstant()))
                .thenReturn(false);

        assertThat(cleanup.cleanUp(MEMBERSHIP_ID, ENDED_AT)).isZero();
        verify(missingCheckOutFinalizer)
                .finalizeOne(101L, MEMBERSHIP_ID, ENDED_AT.toInstant());
    }
}
