package site.omagotchi.learningservice.space.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.mapper.SpacePersistenceMapper;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceJpaPersistenceTest {

    @Mock
    private SpringDataSpaceRepository springDataSpaceRepository;

    @Mock
    private SpacePersistenceMapper spacePersistenceMapper;

    private SpaceJpaPersistence adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpaceJpaPersistence(
                springDataSpaceRepository,
                spacePersistenceMapper
        );
    }

    @Test
    void delegatesNameChecksToNormalizedActiveNameQueries() {
        when(springDataSpaceRepository
                .existsActiveByNormalizedName("회의실 a"))
                .thenReturn(true);
        when(springDataSpaceRepository
                .existsActiveByNormalizedNameAndIdNot(
                        "회의실 a",
                        1L
                ))
                .thenReturn(true);

        assertThat(adapter.existsActiveByName("회의실 a")).isTrue();
        assertThat(adapter.existsActiveByNameAndIdNot(
                "회의실 a",
                1L
        )).isTrue();
    }

    @Test
    void translatesLatestActiveSpaceNameIndexViolation() {
        Space space = mock(Space.class);
        SpaceJpaEntity entity = mock(SpaceJpaEntity.class);
        ConstraintViolationException violation =
                mock(ConstraintViolationException.class);
        when(violation.getConstraintName())
                .thenReturn("uq_spaces_active_name");
        when(spacePersistenceMapper.toEntity(space))
                .thenReturn(entity);
        DataIntegrityViolationException duplicateNameViolation =
                new DataIntegrityViolationException(
                        "duplicate space name",
                        violation
                );
        when(springDataSpaceRepository.saveAndFlush(entity))
                .thenThrow(duplicateNameViolation);

        assertThatThrownBy(() -> adapter.save(space))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(SpaceErrorCode.DUPLICATE_NAME));
    }

    @Test
    void propagatesUnexpectedDataIntegrityViolation() {
        Space space = mock(Space.class);
        SpaceJpaEntity entity = mock(SpaceJpaEntity.class);
        DataIntegrityViolationException unexpected =
                new DataIntegrityViolationException("unexpected constraint");
        when(spacePersistenceMapper.toEntity(space)).thenReturn(entity);
        when(springDataSpaceRepository.saveAndFlush(entity))
                .thenThrow(unexpected);

        assertThatThrownBy(() -> adapter.save(space)).isSameAs(unexpected);
    }

    @Test
    void findByIdUsesUnfilteredRepositoryLookup() {
        SpaceJpaEntity entity = mock(SpaceJpaEntity.class);
        Space space = mock(Space.class);
        when(springDataSpaceRepository.findById(1L))
                .thenReturn(Optional.of(entity));
        when(spacePersistenceMapper.toDomain(entity)).thenReturn(space);

        assertThat(adapter.findById(1L)).containsSame(space);
    }
}
