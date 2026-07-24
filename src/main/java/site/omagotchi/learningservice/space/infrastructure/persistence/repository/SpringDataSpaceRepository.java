package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;

import java.util.List;
import java.util.Optional;

public interface SpringDataSpaceRepository
        extends JpaRepository<SpaceJpaEntity, Long> {

    List<SpaceJpaEntity>
    findAllByDeletedAtIsNullOrderByIdAsc();

    boolean existsByNameAndDeletedAtIsNull(
            String name
    );

    boolean existsByNameAndDeletedAtIsNullAndIdNot(
            String name,
            Long spaceId
    );

    Optional<SpaceJpaEntity>
    findByIdAndDeletedAtIsNull(
            Long spaceId
    );
}