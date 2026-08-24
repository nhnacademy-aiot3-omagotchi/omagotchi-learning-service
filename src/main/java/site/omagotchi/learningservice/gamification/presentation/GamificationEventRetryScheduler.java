package site.omagotchi.learningservice.gamification.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.application.GamificationEventRetryCoordinator;

@Component
@RequiredArgsConstructor
public class GamificationEventRetryScheduler {

    private static final int RETRY_BATCH_SIZE = 100;

    private final GamificationEventRetryCoordinator retryCoordinator;

    @Scheduled(
            fixedDelayString = "${omagotchi.gamification.retry.fixed-delay:30000}",
            initialDelayString = "${omagotchi.gamification.retry.initial-delay:30000}"
    )
    public void retryPendingEvents() {
        retryCoordinator.retryDue(RETRY_BATCH_SIZE);
    }
}
