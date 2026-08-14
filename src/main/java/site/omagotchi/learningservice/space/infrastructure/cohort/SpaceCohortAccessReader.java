package site.omagotchi.learningservice.space.infrastructure.cohort;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpaceCohortAccessReader implements SpaceCohortAccessPort {

    private final CohortAccessService cohortAccessService;

    @Override
    public boolean exists(Long cohortId) {
        return cohortAccessService.exists(cohortId);
    }

    @Override
    public boolean isActiveManager(Long cohortId, UUID userId) {
        try {
            cohortAccessService.requireManager(cohortId, userId);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    @Override
    public List<Long> findActiveManagedCohortIds(UUID userId) {
        return cohortAccessService.findActiveManagedCohortIds(userId);
    }

    @Override
    public List<Long> findActiveCohortIds(UUID userId) {
        return cohortAccessService.findActiveCohortIds(userId);
    }
}
