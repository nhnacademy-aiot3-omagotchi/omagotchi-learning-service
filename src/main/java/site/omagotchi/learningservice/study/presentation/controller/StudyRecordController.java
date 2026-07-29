package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
    private static final String EXPECTED_VERSION_HEADER = "If-Match";

    private final StudyRecordCommandService studyRecordCommandService;
    private final StudyRecordQueryService studyRecordQueryService;

    @GetMapping("/study-records/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> get(
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
            @DateTimeFormat(pattern = "uuuuMMdd") LocalDate aggregationDate
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
            @DateTimeFormat(pattern = "uuuuMM") YearMonth aggregationMonth
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
    public ResponseEntity<StudyRecordResponse> create(
            JwtAuthenticationToken authentication,
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
    public ResponseEntity<StudyRecordResponse> update(
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
    public ResponseEntity<Void> delete(
            JwtAuthenticationToken authentication,
            @RequestHeader(COMMAND_ID_HEADER) UUID commandId,
            @RequestHeader(EXPECTED_VERSION_HEADER) Long expectedVersion,
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
