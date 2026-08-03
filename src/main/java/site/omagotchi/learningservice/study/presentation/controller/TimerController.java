package site.omagotchi.learningservice.study.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.study.application.TimerCommandService;
import site.omagotchi.learningservice.study.application.TimerQueryService;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.presentation.response.CurrentTimerResponse;
import site.omagotchi.learningservice.study.presentation.response.StartTimerResponse;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/timer")
public class TimerController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final TimerCommandService timerCommandService;
    private final TimerQueryService timerQueryService;

    // TODO: command_receipts 구현 후 쓰기 호출의 commandId와 영수증 처리를 연결한다.

    @PostMapping("/start")
    public ResponseEntity<StartTimerResponse> startTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId
    ) {
        TimerStateResult result = timerCommandService.start(
                commandId,
                userId,
                cohortId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StartTimerResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<CurrentTimerResponse> getCurrentTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable Long cohortId
    ) {
        TimerStateResult result = timerQueryService.getCurrent(userId, cohortId);

        return ResponseEntity.ok(CurrentTimerResponse.from(result));
    }

    @PostMapping("/{timerRunId}/stop")
    public ResponseEntity<Void> stopTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        timerCommandService.stop(
                commandId,
                userId,
                cohortId,
                timerRunId
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{timerRunId}/discard")
    public ResponseEntity<Void> discardTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        timerCommandService.discard(
                commandId,
                userId,
                cohortId,
                timerRunId
        );

        return ResponseEntity.noContent().build();
    }
}
