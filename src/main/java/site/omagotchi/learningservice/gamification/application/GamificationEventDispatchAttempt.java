package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GamificationEventDispatchAttempt {

    private final GamificationEventOutboxRepository outboxRepository;
    private final GamificationEventProcessor eventProcessor;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(GamificationEventOutboxRepository.EventKey key) {
        Instant now = clock.instant();
        return outboxRepository.lockPending(key, now)
                .map(outboxEvent -> {
                    eventProcessor.process(outboxEvent.message());
                    outboxRepository.markCompleted(outboxEvent.id(), clock.instant());
                    return true;
                })
                .orElse(false);
    }
}
