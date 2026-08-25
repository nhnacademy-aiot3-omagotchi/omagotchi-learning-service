package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class GamificationEventRetryCoordinator {

    private final GamificationEventOutboxRepository outboxRepository;
    private final GamificationEventDispatchAttempt dispatchAttempt;
    private final GamificationEventFailureRecorder failureRecorder;
    private final Clock clock;

    public void dispatch(GamificationEventOutboxRepository.EventKey key) {
        try {
            dispatchAttempt.process(key);
        } catch (Exception exception) {
            failureRecorder.record(key, exception);
            log.error("게이미피케이션 이벤트 처리에 실패해 재시도를 예약했습니다. type={}, sourceId={}",
                    key.eventType(), key.sourceId(), exception);
        }
    }

    public int retryDue(int limit) {
        Instant now = clock.instant();
        var keys = outboxRepository.findRetryable(now, limit);
        keys.forEach(this::dispatch);
        return keys.size();
    }
}
