package site.omagotchi.learningservice.space.application.port.out;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.Optional;

public interface SpaceRepository {

    boolean existsActiveByName(String name);

    boolean existsActiveByNameAndIdNot(
            String name,
            Long spaceId
    );

    Optional<Space> findActiveById(Long spaceId);

    Space save(Space space);
}