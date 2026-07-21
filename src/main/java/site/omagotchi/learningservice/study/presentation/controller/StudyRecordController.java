package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.study.application.service.StudyRecordService;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study-records")
public class StudyRecordController {

    private final StudyRecordService studyRecordService;

    /**
     * UUID로 특정 기록 가져오기
     */
    @GetMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> get(
            @PathVariable UUID studyRecordId
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping
    public ResponseEntity<StudyRecordResponse> getDaily(
            @RequestParam Long cohortMembershipId,
            @RequestParam LocalDate aggregationDate
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping
    public ResponseEntity<StudyRecordResponse> create(
            @Valid @RequestBody CreateStudyRecordRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PutMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> update(
            @PathVariable UUID studyRecordId,
            @Valid @RequestBody UpdateStudyRecordRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @DeleteMapping("/{studyRecordId}")
    public ResponseEntity<Void> delete(@PathVariable UUID studyRecordId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
