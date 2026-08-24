package site.omagotchi.learningservice.team.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.domain.Team;

import java.util.List;
import java.util.Optional;

/**
 * {@link TeamRepository} 구현.
 *
 * <p>Spring Data 인터페이스({@link TeamJpaRepository})가 Port를 직접 구현하지 않고 이 Class를 둔
 * 이유는 두 가지다 — <b>flush 시점</b>과 <b>실패 변환</b>. 둘 다 기술 세부사항이고,
 * Application에 새면 서비스 코드가 {@code saveAndFlush}와
 * {@code DataIntegrityViolationException}을 직접 다뤄야 한다.</p>
 *
 * <p>DB 인덱스명을 읽어 {@code ErrorCode}로 바꾸는 것도 여기 계층의 일이다.
 * 기술 실패가 하나의 오류 코드와 명확히 대응하고 Application이 재시도·복구를 판단하지
 * 않으므로, 중간 예외 타입을 만들지 않고 곧바로 변환한다.</p>
 *
 * <p>조회는 그대로 위임한다. 위임뿐인 Method가 있다고 이 Class를 없애면 위의 두 책임이
 * Application으로 올라간다.</p>
 */
@Component
@RequiredArgsConstructor
public class TeamJpaPersistence implements TeamRepository {

    private final TeamJpaRepository teamJpaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}가 아니라 {@code saveAndFlush}인 것이 핵심이다. flush가 커밋 시점으로 밀리면
     * 유니크 위반이 이 Method 밖에서 터져 아래 catch에 걸리지 않는다.</p>
     */
    @Override
    public Team save(Team team) {
        try {
            return teamJpaRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException exception) {
            throw TeamConstraintTranslator.translate(exception);
        }
    }

    @Override
    public Optional<Team> findByIdAndDeletedAtIsNull(Long id) {
        return teamJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Long> findActiveCohortId(Long id) {
        return teamJpaRepository.findActiveCohortId(id);
    }

    @Override
    public Optional<Team> findByIdForUpdate(Long id) {
        return teamJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public boolean existsActiveByCohortIdAndName(Long cohortId, String name) {
        return teamJpaRepository.existsActiveByCohortIdAndName(cohortId, name);
    }

    @Override
    public List<Long> findActiveIdsByCohortId(Long cohortId) {
        return teamJpaRepository.findActiveIdsByCohortId(cohortId);
    }

    @Override
    public List<Team> findByIdInAndDeletedAtIsNull(List<Long> ids) {
        return teamJpaRepository.findByIdInAndDeletedAtIsNull(ids);
    }
}
