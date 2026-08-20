package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GamificationEventRetryCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final GamificationEventOutboxRepository.EventKey KEY =
            new GamificationEventOutboxRepository.EventKey(
                    GamificationEventType.ATTENDANCE_CHECKED_IN, "10");

    @Mock
    private GamificationEventOutboxRepository outboxRepository;

    @Mock
    private GamificationEventDispatchAttempt dispatchAttempt;

    @Mock
    private GamificationEventFailureRecorder failureRecorder;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private GamificationEventRetryCoordinator retryCoordinator;

    @BeforeEach
    void setUp() {
        retryCoordinator = new GamificationEventRetryCoordinator(
                outboxRepository, dispatchAttempt, failureRecorder, clock);
    }

    @Test
    void recordsFailureForDurableRetry() {
        IllegalStateException failure = new IllegalStateException("temporary failure");
        doThrow(failure).when(dispatchAttempt).process(KEY);

        retryCoordinator.dispatch(KEY);

        verify(failureRecorder).record(KEY, failure);
    }

    @Test
    void retriesDueEvents() {
        given(outboxRepository.findRetryable(NOW, 100)).willReturn(List.of(KEY));

        assertThat(retryCoordinator.retryDue(100)).isEqualTo(1);

        verify(dispatchAttempt).process(KEY);
    }
}
