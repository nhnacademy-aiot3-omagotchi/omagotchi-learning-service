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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GamificationEventDispatchAttemptTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final GamificationEventOutboxRepository.EventKey KEY =
            new GamificationEventOutboxRepository.EventKey(
                    GamificationEventType.STUDY_COMPLETED, "source-1");
    private static final GamificationEventMessage MESSAGE = new GamificationEventMessage(
            GamificationEventType.STUDY_COMPLETED,
            "source-1",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            NOW);

    @Mock
    private GamificationEventOutboxRepository outboxRepository;

    @Mock
    private GamificationEventProcessor eventProcessor;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private GamificationEventDispatchAttempt dispatchAttempt;

    @BeforeEach
    void setUp() {
        dispatchAttempt = new GamificationEventDispatchAttempt(
                outboxRepository, eventProcessor, clock);
    }

    @Test
    void completesOutboxAfterSuccessfulProcessing() {
        given(outboxRepository.lockPending(KEY, NOW))
                .willReturn(Optional.of(new GamificationEventOutboxRepository.OutboxEvent(1L, MESSAGE)));

        assertThat(dispatchAttempt.process(KEY)).isTrue();

        verify(eventProcessor).process(MESSAGE);
        verify(outboxRepository).markCompleted(1L, NOW);
    }

    @Test
    void leavesOutboxPendingWhenProcessingFails() {
        given(outboxRepository.lockPending(KEY, NOW))
                .willReturn(Optional.of(new GamificationEventOutboxRepository.OutboxEvent(1L, MESSAGE)));
        doThrow(new IllegalStateException("temporary failure"))
                .when(eventProcessor).process(MESSAGE);

        assertThatThrownBy(() -> dispatchAttempt.process(KEY))
                .isInstanceOf(IllegalStateException.class);

        verify(outboxRepository, never()).markCompleted(1L, NOW);
    }
}
