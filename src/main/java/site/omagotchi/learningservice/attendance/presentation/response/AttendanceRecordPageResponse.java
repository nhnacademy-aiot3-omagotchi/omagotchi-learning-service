package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordPageResult;
import site.omagotchi.learningservice.global.presentation.response.PageInfo;

import java.util.List;

public record AttendanceRecordPageResponse(
        List<AttendanceRecordResponse> items,
        PageInfo page
) {
    public AttendanceRecordPageResponse {
        items = List.copyOf(items);
    }

    public static AttendanceRecordPageResponse from(AttendanceRecordPageResult result) {
        return new AttendanceRecordPageResponse(
                result.items().stream().map(AttendanceRecordResponse::from).toList(),
                new PageInfo(result.page(), result.size(), result.totalElements(), result.totalPages())
        );
    }
}
