package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 종료 출결 정리")
class CohortEndedAttendanceCleanupTest {

    private static final List<Long> MEMBERSHIP_IDS = List.of(10L, 11L, 12L);

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private EndedMembershipAttendanceCleanup membershipAttendanceCleanup;

    @InjectMocks
    private CohortEndedAttendanceCleanup cleanup;

    @Test
    @DisplayName("미해결 출결이 있는 소속만 ID 순서대로 정리한다")
    void cleansOnlyTargetMembershipsInOrder() {
        when(attendanceRecordRepository.findDistinctEndCleanupMembershipIds(MEMBERSHIP_IDS))
                .thenReturn(List.of(10L, 12L));
        when(membershipAttendanceCleanup.cleanUp(10L)).thenReturn(2);
        when(membershipAttendanceCleanup.cleanUp(12L)).thenReturn(1);

        assertThat(cleanup.closeAllByCohort(MEMBERSHIP_IDS)).isEqualTo(3);

        var order = inOrder(membershipAttendanceCleanup);
        order.verify(membershipAttendanceCleanup).cleanUp(10L);
        order.verify(membershipAttendanceCleanup).cleanUp(12L);
    }

    @Test
    @DisplayName("한 소속의 실패가 다음 소속 출결 정리를 막지 않는다")
    void oneMembershipFailureDoesNotBlockOthers() {
        when(attendanceRecordRepository.findDistinctEndCleanupMembershipIds(MEMBERSHIP_IDS))
                .thenReturn(List.of(10L, 11L));
        when(membershipAttendanceCleanup.cleanUp(10L))
                .thenThrow(new IllegalStateException("첫 소속 실패"));
        when(membershipAttendanceCleanup.cleanUp(11L)).thenReturn(1);

        assertThat(cleanup.closeAllByCohort(MEMBERSHIP_IDS)).isEqualTo(1);

        verify(membershipAttendanceCleanup).cleanUp(11L);
    }

    @Test
    @DisplayName("기수에 소속이 없으면 저장소도 조회하지 않는다")
    void emptyMembershipsNeedNoQuery() {
        assertThat(cleanup.closeAllByCohort(List.of())).isZero();

        verifyNoInteractions(attendanceRecordRepository, membershipAttendanceCleanup);
    }
}
