package site.omagotchi.learningservice.space.infrastructure.persistence.adapter;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.exception.DuplicateSpaceNameException;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.mapper.SpacePersistenceMapper;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceRepositoryJpaAdapterTest {

    @Mock
    private SpringDataSpaceRepository springDataSpaceRepository;

    @Mock
    private SpacePersistenceMapper spacePersistenceMapper;

    private SpaceRepositoryJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpaceRepositoryJpaAdapter(
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
        when(springDataSpaceRepository.saveAndFlush(entity))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate space name",
                        violation
                ));

        assertThatThrownBy(() -> adapter.save(space))
                .isInstanceOf(DuplicateSpaceNameException.class);
    }
}
