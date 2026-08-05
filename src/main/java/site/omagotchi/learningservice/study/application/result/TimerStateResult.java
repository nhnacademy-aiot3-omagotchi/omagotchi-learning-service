package site.omagotchi.learningservice.study.application.result;

import java.time.Instant;
import java.util.UUID;

public record TimerStateResult(
        UUID timerRunId,
        State state,
        Instant startedAt,
        long elapsedSeconds
) {

    public enum State {
        RUNNING,
        STOPPED
    }

    public static TimerStateResult running(
            UUID timerRunId,
            Instant startedAt,
            long elapsedSeconds
    ) {
        return new TimerStateResult(
                timerRunId,
                State.RUNNING,
                startedAt,
                elapsedSeconds
        );
    }

    public static TimerStateResult stopped() {
        return new TimerStateResult(
                null,
                State.STOPPED,
                null,
                0L
        );
    }
}
