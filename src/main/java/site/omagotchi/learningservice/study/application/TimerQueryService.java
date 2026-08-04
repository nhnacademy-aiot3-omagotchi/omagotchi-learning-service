package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimerQueryService {

    private final CohortAccessService cohortAccessService;
    private final TimerRunQueryRepository timerRunQueryRepository;
    private final Clock clock;
    private final TimerTimePolicy timerTimePolicy;

    public TimerStateResult getCurrent(UUID userId, Long cohortId) {
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        Optional<TimerRun> openTimerRun = timerRunQueryRepository
                .findActiveByCohortMembershipId(cohortMembershipId);

        if (openTimerRun.isEmpty()) {
            return TimerStateResult.stopped();
        }

        TimerRun timerRun = openTimerRun.get();
        Instant currentAt = clock.instant();

        if (!timerRun.isRunningAt(currentAt, timerTimePolicy)) {
            return TimerStateResult.stopped();
        }

        long elapsedSeconds = timerTimePolicy.elapsedSeconds(
                timerRun.getStartedAt(),
                currentAt
        );

        return TimerStateResult.running(
                timerRun.getId(),
                timerRun.getStartedAt(),
                elapsedSeconds
        );
    }
}
