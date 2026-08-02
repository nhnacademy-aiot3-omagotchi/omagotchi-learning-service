package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.TimerStateResult;

import java.time.Instant;
import java.util.UUID;

public record CurrentTimerResponse(
        TimerStateResult.State state,
        UUID timerRunId,
        Instant startedAt,
        long elapsedSeconds
) {

    public static CurrentTimerResponse from(TimerStateResult result) {
        return new CurrentTimerResponse(
                result.state(),
                result.timerRunId(),
                result.startedAt(),
                result.elapsedSeconds()
        );
    }
}
