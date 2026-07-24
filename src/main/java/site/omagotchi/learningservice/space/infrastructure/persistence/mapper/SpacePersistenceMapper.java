package site.omagotchi.learningservice.space.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

@Component
public class SpacePersistenceMapper {

    /**
     * 도메인 객체를 JPA 엔티티로 변환한다.
     */
    public SpaceJpaEntity toEntity(Space space) {
        return SpaceJpaEntity.from(
                space.getId(),
                space.getName(),
                space.getType(),
                space.getCapacity(),
                space.getOperationalStatus(),
                space.getInactiveReason(),
                toOffsetDateTime(space.getCreatedAt()),
                toOffsetDateTime(space.getUpdatedAt()),
                toOffsetDateTime(space.getDeletedAt())
        );
    }

    /**
     * JPA 엔티티를 도메인 객체로 복원한다.
     */
    public Space toDomain(SpaceJpaEntity entity) {
        return Space.restore(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getCapacity(),
                entity.getOperationalStatus(),
                entity.getInactiveReason(),
                toZonedDateTime(entity.getCreatedAt()),
                toZonedDateTime(entity.getUpdatedAt()),
                toZonedDateTime(entity.getDeletedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(
            ZonedDateTime dateTime
    ) {
        return dateTime == null
                ? null
                : dateTime.toOffsetDateTime();
    }

    private ZonedDateTime toZonedDateTime(
            OffsetDateTime dateTime
    ) {
        return dateTime == null
                ? null
                : dateTime.toZonedDateTime();
    }
}