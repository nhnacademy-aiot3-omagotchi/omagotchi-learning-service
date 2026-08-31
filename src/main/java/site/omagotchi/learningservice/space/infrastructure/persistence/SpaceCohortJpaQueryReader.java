package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceCohortQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpaceCohortJpaQueryReader implements SpaceCohortQueryPort {

    private final SpringDataSpaceRepository springDataSpaceRepository;

    @Override
    public Optional<Long> findCohortId(Long spaceId) {
        return springDataSpaceRepository.findCohortIdById(spaceId);
    }

    @Override
    public List<Long> findSpaceIdsByCohortId(Long cohortId) {
        return springDataSpaceRepository.findIdsByCohortId(cohortId);
    }
}
