package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
