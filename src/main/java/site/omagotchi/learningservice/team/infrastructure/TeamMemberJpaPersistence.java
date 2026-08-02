package site.omagotchi.learningservice.team.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.TeamMember;

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

    @Override
    public void delete(TeamMember member) {
        teamMemberJpaRepository.delete(member);
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

    @Override
    public List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId) {
        return teamMemberJpaRepository.findByTeamIdOrderByJoinedAtAsc(teamId);
    }
}
