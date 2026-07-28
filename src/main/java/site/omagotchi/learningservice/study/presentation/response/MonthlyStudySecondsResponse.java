package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;

import java.time.YearMonth;
import java.util.List;

public record MonthlyStudySecondsResponse(
        YearMonth aggregationMonth,
        long totalStudySeconds,
        List<DailyStudySecondsResponse> dailyTotals
) {

    public MonthlyStudySecondsResponse {
        dailyTotals = List.copyOf(dailyTotals);
    }

    public static MonthlyStudySecondsResponse from(MonthlyStudySecondsResult result) {
        return new MonthlyStudySecondsResponse(
                result.aggregationMonth(),
                result.totalStudySeconds(),
                result.dailyTotals().stream()
                        .map(DailyStudySecondsResponse::from)
                        .toList()
        );
    }
}
