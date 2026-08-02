package site.omagotchi.learningservice.study.application.result;

import java.time.YearMonth;
import java.util.List;

public record MonthlyStudySecondsResult(
        YearMonth aggregationMonth,
        long totalStudySeconds,
        List<DailyStudySecondsResult> dailyTotals
) {

    public MonthlyStudySecondsResult {
        dailyTotals = List.copyOf(dailyTotals);
    }
}
