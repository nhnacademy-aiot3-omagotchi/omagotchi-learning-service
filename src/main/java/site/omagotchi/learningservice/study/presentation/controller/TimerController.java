package site.omagotchi.learningservice.study.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.study.presentation.response.CurrentTimerResponse;
import site.omagotchi.learningservice.study.presentation.response.StartTimerResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cohorts/{cohortId}/timer")
public class TimerController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMMAND_ID_HEADER = "X-Command-Id";

    // TODO: command_receipts 구현 후 쓰기 호출의 commandId와 영수증 처리를 연결한다.

    @PostMapping("/start")
    public ResponseEntity<StartTimerResponse> startTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId
    ) {
        // TODO: 타이머 시작 Application 연결
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping
    public ResponseEntity<CurrentTimerResponse> getCurrentTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable Long cohortId
    ) {
        // TODO: 현재 타이머 조회 Application 연결
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/{timerRunId}/stop")
    public ResponseEntity<Void> stopTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        // TODO: 정상 정지와 공부 기록 확정 Application 연결
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/{timerRunId}/discard")
    public ResponseEntity<Void> discardTimer(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID timerRunId
    ) {
        // TODO: 사용자 폐기 Application 연결
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
