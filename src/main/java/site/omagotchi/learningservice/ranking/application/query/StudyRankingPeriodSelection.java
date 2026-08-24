package site.omagotchi.learningservice.ranking.application.query;

import java.time.LocalDate;
import java.time.YearMonth;

public sealed interface StudyRankingPeriodSelection
        permits StudyRankingPeriodSelection.Daily,
        StudyRankingPeriodSelection.Weekly,
        StudyRankingPeriodSelection.Monthly {

    StudyRankingWindow resolve(LocalDate currentAggregationDate);

    static StudyRankingPeriodSelection daily(LocalDate date) {
        return new Daily(date);
    }

    static StudyRankingPeriodSelection weekly(LocalDate weekStartDate) {
        return new Weekly(weekStartDate);
    }

    static StudyRankingPeriodSelection monthly(YearMonth month) {
        return new Monthly(month);
    }

    record Daily(LocalDate date) implements StudyRankingPeriodSelection {
        @Override
        public StudyRankingWindow resolve(LocalDate currentAggregationDate) {
            return StudyRankingWindow.daily(date, currentAggregationDate);
        }
    }

    record Weekly(LocalDate weekStartDate) implements StudyRankingPeriodSelection {

        @Override
        public StudyRankingWindow resolve(LocalDate currentAggregationDate) {
            return StudyRankingWindow.weekly(weekStartDate, currentAggregationDate);
        }
    }

    record Monthly(YearMonth month) implements StudyRankingPeriodSelection {

        @Override
        public StudyRankingWindow resolve(LocalDate currentAggregationDate) {
            return StudyRankingWindow.monthly(month, currentAggregationDate);
        }
    }
}
