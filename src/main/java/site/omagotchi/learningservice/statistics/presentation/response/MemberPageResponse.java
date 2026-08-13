package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.statistics.application.result.MemberPageResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberPageResponse(
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Member> items
) {

    public MemberPageResponse {
        items = List.copyOf(items);
    }

    public static MemberPageResponse from(
            MemberPageResult result
    ) {
        return new MemberPageResponse(
                result.window(),
                result.from(),
                result.to(),
                result.calculatedAt(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.items().stream()
                        .map(Member::from)
                        .toList()
        );
    }

    public record Member(
            Long cohortMembershipId,
            UUID userId,
            long todayStudySeconds,
            long periodStudySeconds,
            long activeStudyDays,
            long recordCount,
            Instant lastStudiedAt
    ) {

        private static Member from(MemberSummaryResult result) {
            return new Member(
                    result.cohortMembershipId(),
                    result.userId(),
                    result.todayStudySeconds(),
                    result.periodStudySeconds(),
                    result.activeStudyDays(),
                    result.recordCount(),
                    result.lastStudiedAt()
            );
        }
    }
}
