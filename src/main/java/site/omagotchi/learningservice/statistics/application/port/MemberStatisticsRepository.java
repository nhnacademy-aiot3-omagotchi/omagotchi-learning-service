package site.omagotchi.learningservice.statistics.application.port;

import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberStatisticsRepository {

    List<MemberSummaryResult> findActiveStudentStatisticsPage(
            Long cohortId,
            LocalDate currentAggregationDate,
            LocalDate from,
            LocalDate to,
            MemberPageQuery query
    );

    long countActiveStudents(Long cohortId);

    Optional<MemberReference> findActiveStudent(
            Long cohortId,
            Long cohortMembershipId
    );

    PeriodSummary summarizeActiveRecords(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to
    );

    List<DailyTotalResult> findMemberDailyStudySeconds(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to
    );

    List<MemberDailyRecordResult> findMemberDailyRecords(
            Long cohortMembershipId,
            LocalDate aggregationDate
    );

    record MemberReference(
            Long cohortMembershipId,
            UUID userId
    ) {
    }

    record PeriodSummary(
            long totalStudySeconds,
            long activeStudyDays,
            long recordCount,
            Instant lastStudiedAt
    ) {
    }
}
