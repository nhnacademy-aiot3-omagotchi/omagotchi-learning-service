package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link VacancyAlertRepository} 구현.
 *
 * <p>{@code saveAndFlush}인 이유는 {@link OccupancyParticipantJpaPersistence}와 같다 —
 * 유니크 위반을 이 Method 안에서 잡아야 기능 오류 코드로 옮길 수 있다.</p>
 */
@Component
@RequiredArgsConstructor
public class VacancyAlertJpaPersistence implements VacancyAlertRepository {

    private final VacancyAlertJpaRepository alertJpaRepository;

    @Override
    public VacancyAlert save(VacancyAlert alert) {
        try {
            return alertJpaRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException exception) {
            throw OccupancyConstraintTranslator.translate(exception);
        }
    }

    @Override
    public List<VacancyAlert> findWaitingByMembershipIds(Collection<Long> cohortMembershipIds) {
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return List.of();
        }
        return alertJpaRepository.findWaitingByMembershipIds(cohortMembershipIds);
    }

    @Override
    public List<WaitingAlert> findWaitingBySpaceId(Long spaceId) {
        return alertJpaRepository.findWaitingBySpaceId(spaceId);
    }

    @Override
    public Optional<VacancyAlert> lockWaitingById(Long alertId) {
        return alertJpaRepository.lockWaitingById(alertId);
    }

    @Override
    public List<Long> deleteWaitingBySpaceId(Long spaceId) {
        return alertJpaRepository.deleteWaitingBySpaceId(spaceId);
    }

    @Override
    public boolean deleteWaiting(Long alertId, Collection<Long> cohortMembershipIds) {
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return false;
        }
        return alertJpaRepository.deleteWaiting(alertId, cohortMembershipIds) > 0;
    }
}
