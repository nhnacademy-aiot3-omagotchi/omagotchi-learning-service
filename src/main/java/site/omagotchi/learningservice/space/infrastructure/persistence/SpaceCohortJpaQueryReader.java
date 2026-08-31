package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceCohortQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.Optional;

/**
 * {@link SpaceCohortQueryPort} 구현.
 *
 * <p>{@code SpaceNameJpaQueryReader}와 같은 이유로 네이티브 쿼리가 아니어도 된다 —
 * 이후에 락을 다시 잡는 흐름이 없다.</p>
 */
@Repository
@RequiredArgsConstructor
public class SpaceCohortJpaQueryReader implements SpaceCohortQueryPort {

    private final SpringDataSpaceRepository springDataSpaceRepository;

    @Override
    public Optional<Long> findCohortId(Long spaceId) {
        return springDataSpaceRepository.findCohortIdById(spaceId);
    }
}
