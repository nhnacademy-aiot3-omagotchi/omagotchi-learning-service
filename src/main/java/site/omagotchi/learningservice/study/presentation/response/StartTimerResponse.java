package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.TimerStateResult;

import java.time.Instant;
import java.util.UUID;

public record StartTimerResponse(
        String resultCode,
        UUID timerRunId,
        TimerStateResult.State state,
        Instant startedAt,
        long elapsedSeconds
) {

    public static StartTimerResponse from(TimerStateResult result) {
        return new StartTimerResponse(
                "TIMER_STARTED",
                result.timerRunId(),
                result.state(),
                result.startedAt(),
                result.elapsedSeconds()
        );
    }
}
