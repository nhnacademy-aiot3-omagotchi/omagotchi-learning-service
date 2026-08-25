package site.omagotchi.learningservice.ranking.application.query;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

public record StudyRankingWindow(
        LocalDate startDate,
        Optional<LocalDate> includedThroughDate
) {

    public StudyRankingWindow {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(includedThroughDate, "includedThroughDate must not be null");
        if (includedThroughDate.filter(date -> date.isBefore(startDate)).isPresent()) {
            throw invalidRequest();
        }
    }

    public static StudyRankingWindow daily(
            LocalDate date,
            LocalDate currentAggregationDate
    ) {
        validateInputs(date, currentAggregationDate);
        if (!date.isBefore(currentAggregationDate)) {
            throw invalidRequest();
        }
        return closed(date, date);
    }

    public static StudyRankingWindow weekly(
            LocalDate weekStartDate,
            LocalDate currentAggregationDate
    ) {
        validateInputs(weekStartDate, currentAggregationDate);
        if (!weekStartDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            throw invalidRequest();
        }

        LocalDate currentWeekStart = currentAggregationDate.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        if (weekStartDate.isAfter(currentWeekStart)) {
            throw invalidRequest();
        }

        return throughLastClosedDate(
                weekStartDate,
                weekStartDate.plusDays(6L),
                currentAggregationDate
        );
    }

    public static StudyRankingWindow monthly(
            YearMonth month,
            LocalDate currentAggregationDate
    ) {
        if (month == null || currentAggregationDate == null) {
            throw invalidRequest();
        }
        if (month.isAfter(YearMonth.from(currentAggregationDate))) {
            throw invalidRequest();
        }

        return throughLastClosedDate(
                month.atDay(1),
                month.atEndOfMonth(),
                currentAggregationDate
        );
    }

    public boolean hasClosedDate() {
        return includedThroughDate.isPresent();
    }

    private static StudyRankingWindow throughLastClosedDate(
            LocalDate startDate,
            LocalDate naturalEndDate,
            LocalDate currentAggregationDate
    ) {
        LocalDate lastClosedDate = currentAggregationDate.minusDays(1L);
        LocalDate endDate = naturalEndDate.isBefore(lastClosedDate)
                ? naturalEndDate
                : lastClosedDate;
        return endDate.isBefore(startDate)
                ? new StudyRankingWindow(startDate, Optional.empty())
                : closed(startDate, endDate);
    }

    private static StudyRankingWindow closed(LocalDate startDate, LocalDate endDate) {
        return new StudyRankingWindow(startDate, Optional.of(endDate));
    }

    private static void validateInputs(LocalDate requestedDate, LocalDate currentAggregationDate) {
        if (requestedDate == null || currentAggregationDate == null) {
            throw invalidRequest();
        }
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
