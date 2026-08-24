package site.omagotchi.learningservice.study.application.port;

import site.omagotchi.learningservice.study.domain.TimerRun;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimerRunQueryRepository {

    Optional<TimerRun> findActiveByCohortMembershipId(Long cohortMembershipId);

    List<TimerRun> findActiveByCohortMembershipIds(Collection<Long> cohortMembershipIds);

    Optional<TimerRun> findOwnedById(UUID timerRunId, Long cohortMembershipId);

    List<TimerRun> findExpirationCandidates(Instant cutoffInclusive, int limit);
}
