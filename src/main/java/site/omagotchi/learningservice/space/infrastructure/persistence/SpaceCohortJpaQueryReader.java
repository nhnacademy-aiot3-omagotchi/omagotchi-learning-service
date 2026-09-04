package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceCohortQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
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

    /**
     * 검증된 잠금 쿼리를 재사용한다. 삭제 여부는 자바에서 거른다 — 새 네이티브
     * 쿼리를 만들면 SpaceCommandService 와 잠금 대상이 갈릴 수 있다.
     */
    @Override
    public Optional<Long> findCohortIdByIdForUpdate(Long spaceId) {
        return springDataSpaceRepository.findByIdForUpdate(spaceId)
                .filter(space -> space.getDeletedAt() == null)
                .map(SpaceJpaEntity::getCohortId);
    }

    @Override
    public List<Long> findAllAssignedSpaceIds() {
        return springDataSpaceRepository.findAllAssignedSpaceIds();
    }

    @Override
    public List<Long> findSpaceIdsByCohortId(Long cohortId) {
        return springDataSpaceRepository.findIdsByCohortId(cohortId);
    }

    @Override
    public List<Long> findUnassignedSpaceIds() {
        return springDataSpaceRepository.findUnassignedSpaceIds();
    }
}
