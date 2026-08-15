package site.omagotchi.learningservice.ranking.infrastructure.persistence.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.QCohortMembership;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.study.domain.QStudyRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudyRankingQueryDslRepository implements StudyRankingRepository {

    private static final QCohortMembership cohortMembership = QCohortMembership.cohortMembership;
    private static final QStudyRecord studyRecord = QStudyRecord.studyRecord;

    private final JPAQueryFactory queryFactory;

    @Override
    public StudyRankingRows findBoard(
            StudyRankingWindow window,
            int maxRank,
            Long cohortId
    ) {
        return find(window, maxRank, cohortId, null, true);
    }

    @Override
    public StudyRankingRows findBoardAndMember(
            StudyRankingWindow window,
            int maxRank,
            Long cohortId,
            Long cohortMembershipId
    ) {
        return find(window, maxRank, cohortId, cohortMembershipId, true);
    }

    @Override
    public StudyRankingRows findMember(
            StudyRankingWindow window,
            Long cohortId,
            Long cohortMembershipId
    ) {
        return find(window, 0, cohortId, cohortMembershipId, false);
    }

    /*
     공동 순위 계산과 maxRank·focusedMembershipId 선택은
     findRankedMembers가 반환한 하나의 집계 결과를 Java에서 처리한다.
     */
    private StudyRankingRows find(
            StudyRankingWindow window,
            int maxRank,
            Long cohortId,
            Long focusedMembershipId,
            boolean includeLeaders
    ) {
        // TODO: 현재 집계일을 포함하는 랭킹에는 실행 중 timer_runs의 경과 시간을
        // 같은 DB 스냅샷에서 중복 없이 합산한다. 현재는 확정된 study_records만 집계한다.
        List<RankedStudyMember> rankedMembers = findRankedMembers(window, cohortId);
        List<RankedStudyMember> leaders = includeLeaders
                ? rankedMembers.stream()
                        .filter(member -> member.rank() <= maxRank)
                        .toList()
                : List.of();
        Optional<RankedStudyMember> focusedMember = rankedMembers.stream()
                .filter(member -> Objects.equals(
                        member.cohortMembershipId(),
                        focusedMembershipId
                ))
                .findFirst();

        return new StudyRankingRows(
                rankedMembers.size(),
                leaders,
                focusedMember
        );
    }

    /*
     SELECT cm.id AS cohort_membership_id,
            cm.user_id,
            COALESCE(SUM(sr.study_seconds), 0) AS study_seconds
     FROM learning_service.cohort_memberships cm
     JOIN learning_service.study_records sr
       ON sr.cohort_membership_id = cm.id
      AND sr.deleted_at IS NULL
      AND sr.aggregation_date BETWEEN :startDate AND :endDate
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
     GROUP BY cm.id, cm.user_id
     HAVING COALESCE(SUM(sr.study_seconds), 0) > 0
     ORDER BY study_seconds DESC,
              cm.id ASC;
     */
    private List<RankedStudyMember> findRankedMembers(
            StudyRankingWindow window,
            Long cohortId
    ) {
        NumberExpression<Long> totalStudySeconds = studyRecord.studySeconds
                .sumLong()
                .coalesce(0L);
        List<Tuple> totals = queryFactory
                .select(
                        cohortMembership.id,
                        cohortMembership.userId,
                        totalStudySeconds
                )
                .from(cohortMembership)
                .join(studyRecord)
                .on(
                        studyRecord.cohortMembershipId.eq(cohortMembership.id),
                        studyRecord.deletedAt.isNull(),
                        studyRecord.aggregationDate.between(
                                window.startDate(),
                                window.endDate()
                        )
                )
                .where(activeStudentOfCohort(cohortId))
                .groupBy(
                        cohortMembership.id,
                        cohortMembership.userId
                )
                .having(totalStudySeconds.gt(0L))
                .orderBy(
                        totalStudySeconds.desc(),
                        cohortMembership.id.asc()
                )
                .fetch();

        return assignCompetitionRanks(totals, totalStudySeconds);
    }

    /*
     ORDER BY study_seconds DESC 결과를 RANK()와 동일한 공동 순위로 변환한다.
     예: 7200, 3600, 3600, 1800 -> 1, 2, 2, 4. Java 후처리 전용
     */
    private List<RankedStudyMember> assignCompetitionRanks(
            List<Tuple> totals,
            NumberExpression<Long> totalStudySeconds
    ) {
        List<RankedStudyMember> rankedMembers = new ArrayList<>(totals.size());
        Long previousStudySeconds = null;
        long rank = 0L;

        for (int index = 0; index < totals.size(); index++) {
            Tuple total = totals.get(index);
            long studySeconds = valueOrZero(total.get(totalStudySeconds));
            if (!Objects.equals(previousStudySeconds, studySeconds)) {
                rank = index + 1L;
                previousStudySeconds = studySeconds;
            }
            rankedMembers.add(new RankedStudyMember(
                    total.get(cohortMembership.id),
                    total.get(cohortMembership.userId),
                    rank,
                    studySeconds
            ));
        }

        return rankedMembers;
    }

    /*
     WHERE cm.cohort_id = :cohortId
       AND cm.role = 'STUDENT'
       AND cm.status = 'ACTIVE'
       AND cm.ended_at IS NULL
     */
    private BooleanExpression activeStudentOfCohort(Long cohortId) {
        return cohortMembership.cohortId.eq(cohortId)
                .and(cohortMembership.role.eq(CohortMembershipRole.STUDENT))
                .and(cohortMembership.status.eq(CohortMembershipStatus.ACTIVE))
                .and(cohortMembership.endedAt.isNull());
    }

    /*
     집계 Tuple의 nullable Long 값을 Application 계약의 0으로 변환한다. Java 후처리 전용
     */
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
