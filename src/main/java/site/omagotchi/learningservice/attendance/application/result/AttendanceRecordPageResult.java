package site.omagotchi.learningservice.attendance.application.result;

import java.util.List;

public record AttendanceRecordPageResult(
        List<AttendanceRecordResult> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AttendanceRecordPageResult {
        items = List.copyOf(items);
    }
}
