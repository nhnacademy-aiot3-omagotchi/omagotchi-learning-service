package site.omagotchi.learningservice.space.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.mapper.SpacePersistenceMapper;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpaceRepositoryJpaAdapter
        implements SpaceRepository {

    private static final String ACTIVE_SPACE_NAME_UNIQUE_INDEX =
            "uq_spaces_active_name";

    private final SpringDataSpaceRepository
            springDataSpaceRepository;

    private final SpacePersistenceMapper
            spacePersistenceMapper;

    @Override
    public boolean existsActiveByName(String name) {
        return springDataSpaceRepository
                .existsActiveByNormalizedName(name);
    }

    @Override
    public boolean existsActiveByNameAndIdNot(
            String name,
            Long spaceId
    ) {
        return springDataSpaceRepository
                .existsActiveByNormalizedNameAndIdNot(
                        name,
                        spaceId
                );
    }

    @Override
    public Optional<Space> findById(
            Long spaceId
    ) {
        return springDataSpaceRepository
                .findById(spaceId)
                .map(spacePersistenceMapper::toDomain);
    }

    @Override
    public Space save(Space space) {
        SpaceJpaEntity entity =
                spacePersistenceMapper.toEntity(space);

        SpaceJpaEntity savedEntity;

        try {
            savedEntity = springDataSpaceRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (isActiveSpaceNameUniqueViolation(exception)) {
                throw new BusinessException(
                        SpaceErrorCode.DUPLICATE_NAME
                );
            }

            throw exception;
        }

        return spacePersistenceMapper.toDomain(
                savedEntity
        );
    }

    private boolean isActiveSpaceNameUniqueViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && ACTIVE_SPACE_NAME_UNIQUE_INDEX.equals(
                    violation.getConstraintName()
            )) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
