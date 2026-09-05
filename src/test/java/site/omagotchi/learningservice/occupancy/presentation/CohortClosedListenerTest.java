package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.event.CohortClosedEvent;
import site.omagotchi.learningservice.occupancy.application.CohortEndedCleanup;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 종료 리스너")
class CohortClosedListenerTest {

    @Mock
    private CohortEndedCleanup cohortEndedCleanup;

    @InjectMocks
    private CohortClosedListener listener;

    @Test
    @DisplayName("이벤트의 기수와 종료 시각을 오케스트레이터에 그대로 전달한다")
    void forwardsClosedAt() {
        var event = new CohortClosedEvent(
                3L,
                OffsetDateTime.parse("2026-09-04T18:00:00+09:00")
        );

        listener.onCohortClosed(event);

        verify(cohortEndedCleanup).cleanUp(event.cohortId(), event.closedAt());
    }
}
