package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;
import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.DailyStudyRecordsResponse;
import site.omagotchi.learningservice.study.presentation.response.MonthlyStudySecondsResponse;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}")
public class StudyRecordController {

    private static final String COMMAND_ID_HEADER = "X-Command-Id";
    private static final String RESOURCE_VERSION_HEADER = "X-RESOURCE-VERSION";

    private final StudyRecordCommandService studyRecordCommandService;
    private final StudyRecordQueryService studyRecordQueryService;

    @GetMapping("/study-records/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> getStudyRecord(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        StudyRecordResult result = studyRecordQueryService.getRecord(
                user.userId(),
                cohortId,
                studyRecordId
        );

        return ResponseEntity.ok(StudyRecordResponse.from(result));
    }

    @GetMapping("/study-records")
    public ResponseEntity<DailyStudyRecordsResponse> getDailyRecords(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate aggregationDate
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        DailyStudyRecordsResult result = studyRecordQueryService.getDailyRecords(
                user.userId(),
                cohortId,
                aggregationDate
        );

        return ResponseEntity.ok(DailyStudyRecordsResponse.from(result));
    }

    @GetMapping("/study-time-summaries")
    public ResponseEntity<MonthlyStudySecondsResponse> getMonthlyStudySeconds(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam("month")
            @DateTimeFormat(pattern = "uuuu-MM") YearMonth aggregationMonth
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        MonthlyStudySecondsResult result = studyRecordQueryService.getMonthlyStudySeconds(
                user.userId(),
                cohortId,
                aggregationMonth
        );

        return ResponseEntity.ok(MonthlyStudySecondsResponse.from(result));
    }

    @PostMapping("/study-records")
    public ResponseEntity<StudyRecordResponse> createStudyRecord(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @Valid @RequestBody CreateStudyRecordRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        StudyRecordResult result = studyRecordCommandService.create(
                commandId,
                user.userId(),
                cohortId,
                request.toCommand()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(StudyRecordResponse.from(result));
    }

    @PutMapping("/study-records/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> updateStudyRecord(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId,
            @Valid @RequestBody UpdateStudyRecordRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        StudyRecordResult result = studyRecordCommandService.update(
                commandId,
                user.userId(),
                cohortId,
                studyRecordId,
                request.toCommand()
        );

        return ResponseEntity.ok(StudyRecordResponse.from(result));
    }

    @DeleteMapping("/study-records/{studyRecordId}")
    public ResponseEntity<Void> deleteStudyRecord(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @RequestHeader(RESOURCE_VERSION_HEADER) Long expectedVersion,
            @PathVariable Long cohortId,
            @PathVariable UUID studyRecordId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        studyRecordCommandService.delete(
                commandId,
                user.userId(),
                cohortId,
                studyRecordId,
                expectedVersion
        );

        return ResponseEntity.noContent().build();
    }
}
