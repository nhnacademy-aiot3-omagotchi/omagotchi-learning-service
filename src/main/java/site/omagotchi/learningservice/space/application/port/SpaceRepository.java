package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.Optional;

public interface SpaceRepository {

    boolean existsActiveByName(String name);

    boolean existsActiveByNameAndIdNot(
            String name,
            Long spaceId
    );

    Optional<Space> findById(Long spaceId);

    Optional<Space> findByIdForUpdate(Long spaceId);

    Space save(Space space);
}
