package site.omagotchi.learningservice.attendance.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.attendance.application.CurrentPresenceQueryService;
import site.omagotchi.learningservice.attendance.presentation.request.ChangeAttendanceStatusRequest;
import site.omagotchi.learningservice.attendance.presentation.request.AttendanceSpaceRequest;
import site.omagotchi.learningservice.attendance.presentation.response.AdminAttendanceRecordPageResponse;
import site.omagotchi.learningservice.attendance.presentation.response.AttendanceRecordPageResponse;
import site.omagotchi.learningservice.attendance.presentation.response.AttendanceRecordResponse;
import site.omagotchi.learningservice.attendance.presentation.response.AttendanceSpaceMoveResponse;
import site.omagotchi.learningservice.attendance.presentation.response.CurrentPresenceResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.time.LocalDate;

/**
 * Request API 규격에 맞춤
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/attendance-records")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentPresenceQueryService currentPresenceQueryService;
    // 입실
    @PostMapping("/check-in")
    public AttendanceRecordResponse checkIn(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceRecordResponse.from(attendanceService.checkIn(
                cohortId,
                user.userId()
        ));
    }

    // 현재 출결의 실습실 이동
    @PostMapping("/move-lab")
    public AttendanceSpaceMoveResponse moveLab(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody AttendanceSpaceRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceSpaceMoveResponse.from(attendanceService.moveLab(
                cohortId,
                user.userId(),
                request.spaceId()
        ), request.spaceId());
    }

    // 현재 출결의 공용 학습 공간 이동
    @PostMapping("/move-study")
    public AttendanceSpaceMoveResponse moveStudySpace(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody AttendanceSpaceRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceSpaceMoveResponse.from(attendanceService.moveStudySpace(
                cohortId,
                user.userId(),
                request.spaceId()
        ), request.spaceId());
    }

    // 현재 열린 체류구간
    @GetMapping("/current-presence")
    public ResponseEntity<CurrentPresenceResponse> getCurrentPresence(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return currentPresenceQueryService.findCurrentPresence(cohortId, user.userId())
                .map(CurrentPresenceResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // 퇴실
    @PostMapping("/check-out")
    public AttendanceRecordResponse checkOut(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceRecordResponse.from(attendanceService.checkOut(cohortId, user.userId()));
    }
    // 나
    @GetMapping("/me")
    public AttendanceRecordPageResponse getMyRecords(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceRecordPageResponse.from(attendanceService.getMyRecords(
                cohortId,
                user.userId(),
                AttendancePageQuery.of(from, to, page, size)
        ));
    }
    /**
     * 기수 관리자의 일자별 출결 목록.
     *
     * <p>본인 조회와 응답을 나눠 쓴다. 이쪽만 소속·계정 식별자를 담는다 —
     * 남의 기록을 여럿 그리는 화면이라 행과 구성원을 잇는 열쇠가 필요하고,
     * 같은 응답을 공유하면 그 열쇠가 본인 조회로도 함께 나간다.</p>
     */
    @GetMapping
    public AdminAttendanceRecordPageResponse getDailyRecords(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AdminAttendanceRecordPageResponse.from(attendanceService.getDailyRecords(
                cohortId,
                user.userId(),
                date,
                AttendancePageQuery.of(date, date, page, size)
        ));
    }
    // 변경된 최종 상태
    @PatchMapping("/{attendance-id}/status")
    public ResponseEntity<Void> changeFinalStatus(
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("attendance-id") Long attendanceId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangeAttendanceStatusRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        attendanceService.changeFinalStatus(cohortId, attendanceId, user.userId(), request.toCommand());
        return ResponseEntity.noContent().build();
    }
}
