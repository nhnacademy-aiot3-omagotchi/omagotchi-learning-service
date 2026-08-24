package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceNameQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.Optional;

/**
 * {@link SpaceNameQueryPort} 구현.
 *
 * <p>{@code SpaceAccessNativeQueryReader}와 달리 네이티브 쿼리가 아니어도 된다. 이후에
 * 락을 다시 잡는 흐름이 없어 1차 캐시가 재검증을 가리는 문제가 애초에 성립하지 않는다.</p>
 */
@Repository
@RequiredArgsConstructor
public class SpaceNameJpaQueryReader implements SpaceNameQueryPort {

    private final SpringDataSpaceRepository springDataSpaceRepository;

    @Override
    public Optional<String> findName(Long spaceId) {
        return springDataSpaceRepository.findNameById(spaceId);
    }
}
