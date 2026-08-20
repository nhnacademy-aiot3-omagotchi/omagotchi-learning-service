package site.omagotchi.learningservice.space.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.mapper.SpacePersistenceMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SpacePersistenceMapperTest {

    private final SpacePersistenceMapper mapper =
            new SpacePersistenceMapper();

    @Test
    void mapsCohortIdFromDomainToEntity() {
        ZonedDateTime now = ZonedDateTime.of(
                2026, 7, 24, 9, 0, 0, 0,
                ZoneOffset.ofHours(9)
        );
        Space space = Space.restore(
                1L,
                42L,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                now,
                now,
                null
        );

        SpaceJpaEntity entity = mapper.toEntity(space);

        assertThat(entity.getCohortId()).isEqualTo(42L);
        assertThat(entity.getName()).isEqualTo("실습실 A");
        assertThat(entity.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
    }

    @Test
    void mapsNullableCohortIdFromEntityToDomain() {
        OffsetDateTime now = OffsetDateTime.of(
                2026, 7, 24, 9, 0, 0, 0,
                ZoneOffset.ofHours(9)
        );
        SpaceJpaEntity entity = SpaceJpaEntity.from(
                1L,
                null,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.INACTIVE,
                null,
                now,
                now,
                null
        );

        Space space = mapper.toDomain(entity);

        assertThat(space.getCohortId()).isNull();
        assertThat(space.getName()).isEqualTo("회의실 A");
        assertThat(space.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
    }
}
