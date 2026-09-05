package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.EndedMembershipAttendanceCleanup;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("종료 소속 점유·출결 조정")
class EndedMembershipPresenceCleanupTest {

    private static final Long MEMBERSHIP_ID = 10L;
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final OffsetDateTime ENDED_AT =
            OffsetDateTime.parse("2026-09-04T18:00:00+09:00");

    @Mock
    private EndedMembershipOccupancyCleanup occupancyCleanup;

    @Mock
    private EndedMembershipAttendanceCleanup attendanceCleanup;

    @InjectMocks
    private EndedMembershipPresenceCleanup presenceCleanup;

    @Test
    @DisplayName("점유·회의를 먼저 정리하고 출결 마감을 이어서 호출한다")
    void cleansOccupancyBeforeAttendance() {
        presenceCleanup.cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT);

        var order = inOrder(occupancyCleanup, attendanceCleanup);
        order.verify(occupancyCleanup).cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT);
        order.verify(attendanceCleanup).cleanUp(MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("점유 정리가 실패해도 출결 정리를 시도한다")
    void occupancyFailureDoesNotBlockAttendance() {
        willThrow(new IllegalStateException("점유 정리 실패"))
                .given(occupancyCleanup).cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT);

        assertThatCode(() -> presenceCleanup.cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT))
                .doesNotThrowAnyException();

        verify(attendanceCleanup).cleanUp(MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("출결 정리 실패는 이미 끝난 점유 정리를 되돌리지 않는다")
    void attendanceFailureIsIsolated() {
        willThrow(new IllegalStateException("출결 정리 실패"))
                .given(attendanceCleanup).cleanUp(MEMBERSHIP_ID);

        assertThatCode(() -> presenceCleanup.cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT))
                .doesNotThrowAnyException();

        verify(occupancyCleanup).cleanUp(MEMBERSHIP_ID, USER_ID, ENDED_AT);
    }
}
