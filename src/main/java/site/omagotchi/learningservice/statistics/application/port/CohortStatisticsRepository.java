package site.omagotchi.learningservice.statistics.application.port;

import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;

import java.time.LocalDate;
import java.util.List;

public interface CohortStatisticsRepository {

    List<MemberTodayStudySeconds> findTodayStudySeconds(
            Long cohortId,
            LocalDate aggregationDate
    );

    List<DailyTotalResult> findDailyStudySeconds(
            Long cohortId,
            LocalDate from,
            LocalDate to
    );

    record MemberTodayStudySeconds(
            Long cohortMembershipId,
            long studySeconds
    ) {
    }
}
