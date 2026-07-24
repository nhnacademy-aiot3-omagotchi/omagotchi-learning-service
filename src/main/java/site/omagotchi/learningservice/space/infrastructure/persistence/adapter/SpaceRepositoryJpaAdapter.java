package site.omagotchi.learningservice.space.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.mapper.SpacePersistenceMapper;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpaceRepositoryJpaAdapter
        implements SpaceRepository {

    private final SpringDataSpaceRepository
            springDataSpaceRepository;

    private final SpacePersistenceMapper
            spacePersistenceMapper;

    @Override
    public boolean existsActiveByName(String name) {
        return springDataSpaceRepository
                .existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsActiveByNameAndIdNot(
            String name,
            Long spaceId
    ) {
        return springDataSpaceRepository
                .existsByNameAndDeletedAtIsNullAndIdNot(
                        name,
                        spaceId
                );
    }

    @Override
    public Optional<Space> findActiveById(
            Long spaceId
    ) {
        return springDataSpaceRepository
                .findByIdAndDeletedAtIsNull(spaceId)
                .map(spacePersistenceMapper::toDomain);
    }

    @Override
    public Space save(Space space) {
        SpaceJpaEntity entity =
                spacePersistenceMapper.toEntity(space);

        SpaceJpaEntity savedEntity =
                springDataSpaceRepository.save(entity);

        return spacePersistenceMapper.toDomain(
                savedEntity
        );
    }
}