package site.omagotchi.learningservice.statistics.presentation.response;

import site.omagotchi.learningservice.global.presentation.response.PageInfo;
import site.omagotchi.learningservice.statistics.application.result.MemberPageResult;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MemberPageResponse(
        String window,
        LocalDate from,
        LocalDate to,
        Instant calculatedAt,
        List<Member> items,
        PageInfo page
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
                result.items().stream()
                        .map(Member::from)
                        .toList(),
                new PageInfo(
                        result.page(),
                        result.size(),
                        result.totalElements(),
                        result.totalPages()
                )
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
