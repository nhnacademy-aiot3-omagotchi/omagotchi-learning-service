package site.omagotchi.learningservice.study.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.annotation.CurrentCohortMembership;
import site.omagotchi.learningservice.study.application.command.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.query.StudyRecordQueryService;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/study-records")
public class StudyRecordController {

    private final StudyRecordCommandService studyRecordCommandService;
    private final StudyRecordQueryService studyRecordQueryService;

    /**
     * UUID를 기반으로, 기록 하나의 상세 정보를 가져옵니다.
     */
    @GetMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> get(
            @CurrentCohortMembership Long cohortMembershipId,
            @PathVariable UUID studyRecordId
    ) {
        StudyRecordResult result = studyRecordQueryService.getRecord(
                cohortMembershipId,
                studyRecordId
        );

        return ResponseEntity.status(HttpStatus.OK).body(StudyRecordResponse.from(result));
    }

    /**
     * cohortMembershipId 해당하는 aggregationDate 날짜의 공부 기록을 모두 가져옵니다. (반환 구조 미정)
     */
    @GetMapping
    public ResponseEntity<StudyRecordResponse> getDaily(
            @CurrentCohortMembership Long cohortMembershipId,
            @RequestParam LocalDate aggregationDate
    ) {
        // TODO(REC-005, DAT-001, DAT-005): 저장된 aggregationDate 기준의 활성 기록 목록을 반환한다.
        // TODO: 단건 응답을 목록 응답으로 변경하고 페이지네이션 적용 여부를 결정한다.

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * cohortMembershipId의 새 기록을 작성합니다.
     */
    @PostMapping
    public ResponseEntity<StudyRecordResponse> create(
            @RequestHeader("X-Command-Id") UUID commandId,
            @CurrentCohortMembership Long cohortMembershipId,
            @Valid @RequestBody CreateStudyRecordRequest request
    ) {
        StudyRecordResult result = studyRecordCommandService.create(
                commandId,
                cohortMembershipId,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(StudyRecordResponse.from(result));
    }

    /**
     * cohortMembershipId의 studyRecordId 기록을 수정합니다.
     */
    @PutMapping("/{studyRecordId}")
    public ResponseEntity<StudyRecordResponse> update(
            @RequestHeader("X-Command-Id") UUID commandId,
            @CurrentCohortMembership Long cohortMembershipId,
            @PathVariable UUID studyRecordId,
            @Valid @RequestBody UpdateStudyRecordRequest request
    ) {
        StudyRecordResult result = studyRecordCommandService.update(
                commandId,
                cohortMembershipId,
                studyRecordId,
                request
        );

        return ResponseEntity.status(HttpStatus.OK).body(StudyRecordResponse.from(result));
    }

    /**
     * cohortMembershipId의 studyRecordId 기록을 삭제합니다.
     */
    @DeleteMapping("/{studyRecordId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Command-Id") UUID commandId,
            @CurrentCohortMembership Long cohortMembershipId,
            @PathVariable UUID studyRecordId
    ) {
        studyRecordCommandService.delete(
                commandId,
                cohortMembershipId,
                studyRecordId
        );

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
