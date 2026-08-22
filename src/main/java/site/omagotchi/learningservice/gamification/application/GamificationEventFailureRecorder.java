package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class GamificationEventFailureRecorder {

    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);
    private static final int ERROR_MESSAGE_LIMIT = 1000;

    private final GamificationEventOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            GamificationEventOutboxRepository.EventKey key,
            Exception exception
    ) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (message.length() > ERROR_MESSAGE_LIMIT) {
            message = message.substring(0, ERROR_MESSAGE_LIMIT);
        }
        outboxRepository.recordFailure(key, clock.instant().plus(RETRY_DELAY), message);
    }
}
