package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordPageResult;
import site.omagotchi.learningservice.global.presentation.response.PageInfo;

import java.util.List;

public record AdminAttendanceRecordPageResponse(
        List<AdminAttendanceRecordResponse> items,
        PageInfo page
) {
    public AdminAttendanceRecordPageResponse {
        items = List.copyOf(items);
    }

    public static AdminAttendanceRecordPageResponse from(AttendanceRecordPageResult result) {
        return new AdminAttendanceRecordPageResponse(
                result.items().stream().map(AdminAttendanceRecordResponse::from).toList(),
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
        );
    }
}
