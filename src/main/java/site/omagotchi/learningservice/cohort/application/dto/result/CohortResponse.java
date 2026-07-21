package site.omagotchi.learningservice.cohort.application.dto.result;

import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 기수 기본 정보 조회 결과
 */
public record CohortResponse(
        Long id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        CohortStatus status,
        Long createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CohortResponse from(Cohort cohort) {
        return new CohortResponse(
            cohort.getId(),
            cohort.getName(),
            cohort.getDescription(),
            cohort.getStartDate(),
            cohort.getEndDate(),
            cohort.getStatus(),
            cohort.getCreatedByUserId(),
            cohort.getCreatedAt(),
            cohort.getUpdatedAt()
        );
    }
}
