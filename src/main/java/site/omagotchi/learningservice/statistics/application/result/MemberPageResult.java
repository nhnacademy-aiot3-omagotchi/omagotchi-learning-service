package site.omagotchi.learningservice.statistics.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MemberPageResult(
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<MemberSummaryResult> items
) {

    public MemberPageResult {
        items = List.copyOf(items);
    }
}
