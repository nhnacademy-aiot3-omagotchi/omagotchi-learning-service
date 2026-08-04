package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.study.presentation.request.StartTimerRequest;
import site.omagotchi.learningservice.study.presentation.request.StopTimerRequest;
import site.omagotchi.learningservice.study.presentation.response.CurrentTimerResponse;
import site.omagotchi.learningservice.study.presentation.response.StartTimerResponse;

@RestController
@RequestMapping("/api/v1/cohorts/{cohortId}/timer")
public class TimerController {

    @PostMapping("/start")
    public ResponseEntity<StartTimerResponse> startTimer(
            @PathVariable Long cohortId,
            @Valid @RequestBody StartTimerRequest request
    ) {
        // TODO(TMR-001, DAT-001): 타이머 시작 시각을 보존하고 종료 시 분할 기준으로 전달한다.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping
    public ResponseEntity<CurrentTimerResponse> getCurrentTimer(
            @PathVariable Long cohortId
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopTimer(
            @PathVariable Long cohortId,
            @Valid @RequestBody StopTimerRequest request
    ) {
        // TODO(TMR-004, DAT-001~003): TimerCommandService가 종료를 확정한 뒤 분할 저장 Service를 같은 트랜잭션에서 호출한다.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
