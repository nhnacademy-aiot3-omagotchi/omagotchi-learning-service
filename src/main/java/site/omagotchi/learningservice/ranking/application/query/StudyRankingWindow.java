package site.omagotchi.learningservice.ranking.application.query;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record StudyRankingWindow(
        LocalDate startDate,
        LocalDate endDate
) {

    public static StudyRankingWindow resolve(
            StudyRankingPeriod period,
            Instant calculatedAt
    ) {
        if (period == null || calculatedAt == null) {
            throw invalidRequest();
        }

        LocalDate currentAggregationDate = StudyTimePolicy.aggregationDate(calculatedAt);
        return switch (period) {
            case DAILY -> new StudyRankingWindow(
                    currentAggregationDate,
                    currentAggregationDate
            );
            case WEEKLY -> new StudyRankingWindow(
                    currentAggregationDate.with(
                            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    ),
                    currentAggregationDate
            );
            case MONTHLY -> new StudyRankingWindow(
                    currentAggregationDate.withDayOfMonth(1),
                    currentAggregationDate
            );
        };
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
