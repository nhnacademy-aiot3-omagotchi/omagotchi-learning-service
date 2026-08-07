package site.omagotchi.learningservice.study.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
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

    private static final String COMMAND_ID_HEADER = "X-Command-Id";

    private final TimerCommandService timerCommandService;
    private final TimerQueryService timerQueryService;

    @PostMapping("/start")
    public ResponseEntity<StartTimerResponse> startTimer(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        TimerStateResult result = timerCommandService.start(
                commandId,
                user.userId(),
                cohortId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StartTimerResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<CurrentTimerResponse> getCurrentTimer(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        TimerStateResult result = timerQueryService.getCurrent(user.userId(), cohortId);

        return ResponseEntity.ok(CurrentTimerResponse.from(result));
    }

    @PostMapping("/{timerRunId}/stop")
    public ResponseEntity<Void> stopTimer(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        timerCommandService.stop(
                commandId,
                user.userId(),
                cohortId,
                timerRunId
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{timerRunId}/discard")
    public ResponseEntity<Void> discardTimer(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        timerCommandService.discard(
                commandId,
                user.userId(),
                cohortId,
                timerRunId
        );

        return ResponseEntity.noContent().build();
    }
}
