package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.domain.QTimerRun;
import site.omagotchi.learningservice.study.domain.TimerRun;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TimerRunQueryDslRepository implements TimerRunQueryRepository {

    private static final QTimerRun timerRun = QTimerRun.timerRun;

    private final JPAQueryFactory queryFactory;

    /*
     * SELECT tr.*
     * FROM learning_service.timer_runs tr
     * WHERE tr.cohort_membership_id = :cohortMembershipId
     *   AND tr.ended_at IS NULL;
     */
    @Override
    public Optional<TimerRun> findActiveByCohortMembershipId(Long cohortMembershipId) {
        TimerRun result = queryFactory
                .selectFrom(timerRun)
                .where(
                        timerRun.cohortMembershipId.eq(cohortMembershipId),
                        timerRun.endedAt.isNull()
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /*
     * SELECT tr.*
     * FROM learning_service.timer_runs tr
     * WHERE tr.cohort_membership_id IN (:cohortMembershipIds)
     *   AND tr.ended_at IS NULL
     * ORDER BY tr.cohort_membership_id ASC,
     *          tr.started_at ASC,
     *          tr.id ASC;
     */
    @Override
    public List<TimerRun> findActiveByCohortMembershipIds(
            Collection<Long> cohortMembershipIds
    ) {
        return queryFactory
                .selectFrom(timerRun)
                .where(
                        timerRun.cohortMembershipId.in(cohortMembershipIds),
                        timerRun.endedAt.isNull()
                )
                .orderBy(
                        timerRun.cohortMembershipId.asc(),
                        timerRun.startedAt.asc(),
                        timerRun.id.asc()
                )
                .fetch();
    }

    /*
     * SELECT tr.*
     * FROM learning_service.timer_runs tr
     * WHERE tr.id = :timerRunId
     *   AND tr.cohort_membership_id = :cohortMembershipId
     * ;
     */
    @Override
    public Optional<TimerRun> findOwnedById(UUID timerRunId, Long cohortMembershipId) {
        TimerRun result = queryFactory
                .selectFrom(timerRun)
                .where(
                        timerRun.id.eq(timerRunId),
                        timerRun.cohortMembershipId.eq(cohortMembershipId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /*
     * SELECT tr.*
     * FROM learning_service.timer_runs tr
     * WHERE tr.ended_at IS NULL
     *   AND tr.started_at <= :cutoffInclusive
     * ORDER BY tr.started_at ASC,
     *          tr.id ASC
     * FETCH FIRST :limit ROWS ONLY;
     */
    @Override
    public List<TimerRun> findExpirationCandidates(Instant cutoffInclusive, int limit) {
        return queryFactory
                .selectFrom(timerRun)
                .where(
                        timerRun.endedAt.isNull(),
                        timerRun.startedAt.loe(cutoffInclusive)
                )
                .orderBy(
                        timerRun.startedAt.asc(),
                        timerRun.id.asc()
                )
                .limit(limit)
                .fetch();
    }
}
