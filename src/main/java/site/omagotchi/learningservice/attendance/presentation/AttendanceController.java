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
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.attendance.presentation.request.ChangeAttendanceStatusRequest;
import site.omagotchi.learningservice.attendance.presentation.response.AttendanceRecordResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

/**
 * Request API 규격에 맞춤
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/attendance-records")
public class AttendanceController {

    private final AttendanceService attendanceService;
    // 입실
    @PostMapping("/check-in")
    public AttendanceRecordResponse checkIn(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceRecordResponse.from(attendanceService.checkIn(cohortId, user.userId()));
    }
    // 퇴실
    @PostMapping("/check-out")
    public AttendanceRecordResponse checkOut(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return AttendanceRecordResponse.from(attendanceService.checkOut(cohortId, user.userId()));
    }
    // 나
    @GetMapping("/me")
    public List<AttendanceRecordResponse> getMyRecords(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return attendanceService.getMyRecords(cohortId, user.userId()).stream()
                .map(AttendanceRecordResponse::from)
                .toList();
    }
    // 기수 Id
    @GetMapping
    public List<AttendanceRecordResponse> getDailyRecords(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return attendanceService.getDailyRecords(cohortId, user.userId(), date).stream()
                .map(AttendanceRecordResponse::from)
                .toList();
    }
    // 변경된 최종 상태
    @PatchMapping("/{attendance-id}/status")
    public ResponseEntity<Void> changeFinalStatus(
            @PathVariable Long cohortId,
            @PathVariable("attendance-id") Long attendanceId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangeAttendanceStatusRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        attendanceService.changeFinalStatus(cohortId, attendanceId, user.userId(), request.toCommand());
        return ResponseEntity.noContent().build();
    }
}
