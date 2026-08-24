package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * System Admin 전체 기수 화면에 필요한 집계 응답.
 */
public record CohortAdminSummaryResponse(
        Long id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        CohortStatus status,
        long memberCount,
        List<UUID> managerUserIds
) {
    public static CohortAdminSummaryResponse from(
            Cohort cohort,
            long memberCount,
            List<UUID> managerUserIds
    ) {
        return new CohortAdminSummaryResponse(
                cohort.getId(),
                cohort.getName(),
                cohort.getDescription(),
                cohort.getStartDate(),
                cohort.getEndDate(),
                cohort.getStatus(),
                memberCount,
                List.copyOf(managerUserIds)
        );
    }
}
