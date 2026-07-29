package site.omagotchi.learningservice.attendance.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.attendance.presentation.request.ChangeAttendanceStatusRequest;
import site.omagotchi.learningservice.attendance.presentation.response.AttendanceRecordResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request API 규격에 맞춤
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/attendance-records")
public class AttendanceController {

    private final AttendanceService attendanceService;
    // 입실
    @PostMapping("/check-in")
    public AttendanceRecordResponse checkIn(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return AttendanceRecordResponse.from(attendanceService.checkIn(cohortId, userId));
    }
    // 퇴실
    @PostMapping("/check-out")
    public AttendanceRecordResponse checkOut(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return AttendanceRecordResponse.from(attendanceService.checkOut(cohortId, userId));
    }
    // 나
    @GetMapping("/me")
    public List<AttendanceRecordResponse> getMyRecords(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return attendanceService.getMyRecords(cohortId, userId).stream()
                .map(AttendanceRecordResponse::from)
                .toList();
    }
    // 기수 Id
    @GetMapping
    public List<AttendanceRecordResponse> getDailyRecords(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return attendanceService.getDailyRecords(cohortId, userId, date).stream()
                .map(AttendanceRecordResponse::from)
                .toList();
    }
    // 변경된 최종 상태
    @PatchMapping("/{attendance-id}/status")
    public ResponseEntity<Void> changeFinalStatus(
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("attendance-id") Long attendanceId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ChangeAttendanceStatusRequest request
    ) {
        attendanceService.changeFinalStatus(cohortId, attendanceId, userId, request.toCommand());
        return ResponseEntity.noContent().build();
    }
}
