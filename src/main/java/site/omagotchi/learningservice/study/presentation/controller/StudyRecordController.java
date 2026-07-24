// package site.omagotchi.learningservice.study.presentation.controller;
// 
// import jakarta.validation.Valid;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;
// import site.omagotchi.learningservice.study.application.command.StudyRecordCommandService;
// import site.omagotchi.learningservice.study.application.query.StudyRecordQueryService;
// import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
// import site.omagotchi.learningservice.study.presentation.request.CreateStudyRecordRequest;
// import site.omagotchi.learningservice.study.presentation.request.UpdateStudyRecordRequest;
// import site.omagotchi.learningservice.study.presentation.response.StudyRecordResponse;
// 
// import java.time.LocalDate;
// import java.util.UUID;
// 
// /**
//  * TODO(임시): 현재 브랜치에는 인증 컨텍스트와 cohortMembership 조회 기능이 없다.
//  * 소속 조회가 구현되기 전까지 X-User-Id의 userId를 cohortMembershipId 입력값으로 전달한다.
//  * 인증 연동 후에는 임시 헤더를 제거하고 검증된 JWT sub와 cohortId로 ACTIVE 소속을 조회한다.
//  */
// @RestController
// @RequiredArgsConstructor
// @RequestMapping("/api/v1/cohorts/{cohortId}/study-records")
// public class StudyRecordController {
// 
//     private static final String TEMPORARY_USER_ID_HEADER = "X-User-Id";
// 
//     private final StudyRecordCommandService studyRecordCommandService;
//     private final StudyRecordQueryService studyRecordQueryService;
// 
//     @GetMapping("/{studyRecordId}")
//     public ResponseEntity<StudyRecordResponse> get(
//             @PathVariable Long cohortId,
//             @RequestHeader(TEMPORARY_USER_ID_HEADER) Long userId,
//             @PathVariable UUID studyRecordId
//     ) {
//         StudyRecordResult result = studyRecordQueryService.getRecord(
//                 userId,
//                 studyRecordId
//         );
// 
//         return ResponseEntity.status(HttpStatus.OK).body(StudyRecordResponse.from(result));
//     }
// 
//     /**
//      * 집계 일자 범위의 공부 기록을 조회한다. (반환 구조 미정)
//      */
//     @GetMapping
//     public ResponseEntity<StudyRecordResponse> getRecords(
//             @PathVariable Long cohortId,
//             @RequestHeader(TEMPORARY_USER_ID_HEADER) Long userId,
//             @RequestParam LocalDate fromAggregationDate,
//             @RequestParam LocalDate toAggregationDate,
//             @RequestParam(defaultValue = "0") Integer page,
//             @RequestParam(defaultValue = "20") Integer size
//     ) {
//         // TODO(REC-005, DAT-004~005): 집계 일자 범위의 활성 기록 목록과 전체 공부 초를 반환한다.
//         // TODO: 목록 응답 DTO와 페이지네이션 조회를 구현한다.
// 
//         return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
//     }
// 
//     @PostMapping
//     public ResponseEntity<StudyRecordResponse> create(
//             @PathVariable Long cohortId,
//             @RequestHeader(TEMPORARY_USER_ID_HEADER) Long userId,
//             @RequestHeader("X-Command-Id") UUID commandId,
//             @Valid @RequestBody CreateStudyRecordRequest request
//     ) {
//         StudyRecordResult result = studyRecordCommandService.create(
//                 commandId,
//                 userId,
//                 request
//         );
// 
//         return ResponseEntity.status(HttpStatus.CREATED).body(StudyRecordResponse.from(result));
//     }
// 
//     @PutMapping("/{studyRecordId}")
//     public ResponseEntity<StudyRecordResponse> update(
//             @PathVariable Long cohortId,
//             @RequestHeader(TEMPORARY_USER_ID_HEADER) Long userId,
//             @RequestHeader("X-Command-Id") UUID commandId,
//             @PathVariable UUID studyRecordId,
//             @Valid @RequestBody UpdateStudyRecordRequest request
//     ) {
//         StudyRecordResult result = studyRecordCommandService.update(
//                 commandId,
//                 userId,
//                 studyRecordId,
//                 request
//         );
// 
//         return ResponseEntity.status(HttpStatus.OK).body(StudyRecordResponse.from(result));
//     }
// 
//     @DeleteMapping("/{studyRecordId}")
//     public ResponseEntity<Void> delete(
//             @PathVariable Long cohortId,
//             @RequestHeader(TEMPORARY_USER_ID_HEADER) Long userId,
//             @RequestHeader("X-Command-Id") UUID commandId,
//             @PathVariable UUID studyRecordId
//     ) {
//         studyRecordCommandService.delete(
//                 commandId,
//                 userId,
//                 studyRecordId
//         );
// 
//         return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//     }
// }
