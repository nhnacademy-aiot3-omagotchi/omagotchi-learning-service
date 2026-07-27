package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/study-records")
public class StudyRecordController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String COMMAND_ID_HEADER = "X-Command-Id";
    private static final String EXPECTED_VERSION_HEADER = "If-Match";

    private final StudyRecordCommandService studyRecordCommandService;
    private final StudyRecordQueryService studyRecordQueryService;

    @GetMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> get(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId
    ) {
        StudyRecordResult result = studyRecordQueryService.getRecord(
                userId,
                cohortId,
                studyRecordId
        );

        return ResponseEntity.ok(StudyRecordResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<StudyRecordResponse> create(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @Valid @RequestBody CreateStudyRecordRequest request
    ) {
        StudyRecordResult result = studyRecordCommandService.create(
                commandId,
                userId,
                cohortId,
                request.toCommand()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(StudyRecordResponse.from(result));
    }

    @PutMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> update(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId,
            @Valid @RequestBody UpdateStudyRecordRequest request
    ) {
        StudyRecordResult result = studyRecordCommandService.update(
                commandId,
                userId,
                cohortId,
                studyRecordId,
                request.toCommand()
        );

        return ResponseEntity.ok(StudyRecordResponse.from(result));
    }

    @DeleteMapping("/{studyRecordId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @RequestHeader(EXPECTED_VERSION_HEADER) Long expectedVersion,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId
    ) {
        studyRecordCommandService.delete(
                commandId,
                userId,
                cohortId,
                studyRecordId,
                expectedVersion
        );

        return ResponseEntity.noContent().build();
    }
}
