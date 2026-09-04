package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipPresenceCleanup;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("소속 종료 점유·출결 리스너")
class OccupancyMembershipEndedListenerTest {

    @Mock
    private EndedMembershipPresenceCleanup presenceCleanup;

    @InjectMocks
    private OccupancyMembershipEndedListener listener;

    @Test
    @DisplayName("이벤트의 소속·계정·종료 시각을 조정 서비스에 그대로 전달한다")
    void forwardsEventValues() {
        var event = new CohortMembershipEndedEvent(
                10L,
                3L,
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                OffsetDateTime.parse("2026-09-04T18:00:00+09:00")
        );

        listener.onMembershipEnded(event);

        verify(presenceCleanup).cleanUp(
                event.membershipId(), event.userId(), event.endedAt());
    }
}
