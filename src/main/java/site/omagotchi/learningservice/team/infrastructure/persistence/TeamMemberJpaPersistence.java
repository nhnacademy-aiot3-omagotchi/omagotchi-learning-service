package site.omagotchi.learningservice.team.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link TeamMemberRepository} 구현.
 *
 * <p>{@link TeamJpaPersistence}와 같은 이유로 존재한다 — flush 시점 고정과 인덱스 위반의
 * {@code ErrorCode} 변환. 팀원 쪽 유니크가 셋(멤버십 단독, 팀+멤버십, 팀당 MASTER)이라
 * 변환 분기도 여기 붙는 편이 자연스럽다.</p>
 */
@Component
@RequiredArgsConstructor
public class TeamMemberJpaPersistence implements TeamMemberRepository {

    private final TeamMemberJpaRepository teamMemberJpaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}가 아니라 {@code saveAndFlush}인 것이 핵심이다. flush가 커밋 시점으로 밀리면
     * 유니크 위반이 이 Method 밖에서 터져 아래 catch에 걸리지 않는다.</p>
     */
    @Override
    public TeamMember save(TeamMember member) {
        try {
            return teamMemberJpaRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw TeamConstraintTranslator.translate(exception);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}와 같이 flush까지 여기서 끝낸다. Spring Data에 {@code deleteAndFlush}가
     * 없어 두 줄이 됐을 뿐, 의도는 {@code saveAndFlush}와 동일하다 — DELETE의 실행 시점을
     * 어댑터 안으로 고정해 호출 순서와 SQL 순서를 일치시킨다.</p>
     */
    @Override
    public void delete(TeamMember member) {
        teamMemberJpaRepository.delete(member);
        teamMemberJpaRepository.flush();
    }

    @Override
    public Optional<TeamMember> findById(Long id) {
        return teamMemberJpaRepository.findById(id);
    }

    @Override
    public boolean existsByCohortMembershipId(Long cohortMembershipId) {
        return teamMemberJpaRepository.existsByCohortMembershipId(cohortMembershipId);
    }

    @Override
    public List<TeamMember> findByCohortMembershipIdIn(Collection<Long> cohortMembershipIds) {
        return teamMemberJpaRepository.findByCohortMembershipIdIn(cohortMembershipIds);
    }

    @Override
    public Optional<TeamMember> findByTeamIdAndCohortMembershipId(Long teamId, Long cohortMembershipId) {
        return teamMemberJpaRepository.findByTeamIdAndCohortMembershipId(teamId, cohortMembershipId);
    }

    @Override
    public long countByTeamId(Long teamId) {
        return teamMemberJpaRepository.countByTeamId(teamId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code LIMIT}을 {@link Pageable}로 거는 것은 JPQL에 그 문법이 없기 때문이다.
     * 정렬은 쿼리의 {@code order by}가 이미 갖고 있으므로 {@link Pageable}에는 크기만 준다 —
     * 양쪽에 정렬을 두면 Spring Data가 둘을 합쳐 중복된 {@code ORDER BY}를 만든다.</p>
     */
    @Override
    public List<MembershipRef> findMembershipRefsAfter(Long afterId, int limit) {
        return teamMemberJpaRepository
                .findMembershipRefsAfter(afterId, PageRequest.ofSize(limit))
                .stream()
                .map(projection -> new MembershipRef(
                        projection.getTeamMemberId(),
                        projection.getCohortMembershipId()))
                .toList();
    }

    @Override
    public List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId) {
        return teamMemberJpaRepository.findByTeamIdOrderByJoinedAtAsc(teamId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>트랜잭션 밖에서 부르면 락이 즉시 해제되어 아무것도 보장하지 못하므로 조용히
     * 통과시키지 않고 명시적으로 막는다 ({@code RoomOccupancyJpaPersistence#lockById}와 같은 방어).</p>
     */
    @Override
    public List<TeamMember> lockAllByTeamId(Long teamId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "팀원 행 락은 트랜잭션 안에서만 획득할 수 있습니다. teamId=" + teamId);
        }
        return teamMemberJpaRepository.findAllByTeamIdForUpdate(teamId);
    }

    @Override
    public List<TeamMember> findSuccessorCandidates(Long teamId, Long excludedMemberId) {
        return teamMemberJpaRepository.findSuccessorCandidates(teamId, excludedMemberId);
    }

    @Override
    public void deleteByTeamId(Long teamId) {
        teamMemberJpaRepository.deleteByTeamId(teamId);
    }

    @Override
    public long countByTeamIdAndRole(Long teamId, TeamMemberRole role) {
        return teamMemberJpaRepository.countByTeamIdAndRole(teamId, role);
    }

    @Override
    public Optional<Long> findTeamIdByCohortMembershipId(Long cohortMembershipId) {
        return teamMemberJpaRepository.findTeamIdByCohortMembershipId(cohortMembershipId);
    }
}
