package site.omagotchi.learningservice.space.infrastructure.persistence.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.out.SpaceQueryPort;
import site.omagotchi.learningservice.space.application.query.SpaceListItem;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.RoomOccupancyJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SpaceQueryJpaAdapter implements SpaceQueryPort {

    private final SpringDataSpaceRepository spaceRepository;

    private final SpringDataRoomOccupancyRepository
            roomOccupancyRepository;

    @Override
    public List<SpaceListItem> findAllSpacesWithStatus(
            ZonedDateTime now
    ) {
        List<SpaceJpaEntity> spaces =
                spaceRepository
                        .findAllByDeletedAtIsNullOrderByIdAsc();

        if (spaces.isEmpty()) {
            return List.of();
        }

        List<Long> spaceIds = spaces.stream()
                .map(SpaceJpaEntity::getId)
                .toList();

        List<RoomOccupancyJpaEntity> occupancies =
                roomOccupancyRepository
                        .findAllActiveBySpaceIds(
                                spaceIds,
                                now.toOffsetDateTime()
                        );

        Map<Long, RoomOccupancyJpaEntity>
                occupancyBySpaceId =
                createOccupancyMap(occupancies);

        return spaces.stream()
                .map(space -> toListItem(
                        space,
                        occupancyBySpaceId.get(space.getId()),
                        now
                ))
                .toList();
    }

    private Map<Long, RoomOccupancyJpaEntity>
    createOccupancyMap(
            List<RoomOccupancyJpaEntity> occupancies
    ) {
        Map<Long, RoomOccupancyJpaEntity> result =
                new HashMap<>();

        /*
         * Repository에서 startedAt 내림차순으로 조회한다.
         *
         * 혹시 동일 공간에 활성 점유 데이터가 여러 건 있어도
         * 가장 최근 점유 하나만 사용한다.
         */
        for (RoomOccupancyJpaEntity occupancy : occupancies) {
            result.putIfAbsent(
                    occupancy.getSpaceId(),
                    occupancy
            );
        }

        return result;
    }

    private SpaceListItem toListItem(
            SpaceJpaEntity space,
            RoomOccupancyJpaEntity occupancy,
            ZonedDateTime now
    ) {
        boolean occupied = occupancy != null;

        SpaceUsageStatus status = occupied
                ? SpaceUsageStatus.IN_USE
                : SpaceUsageStatus.AVAILABLE;

        ZonedDateTime occupancyExpiresAt =
                occupancy == null
                        ? null
                        : occupancy.getExpiresAt()
                        .atZoneSameInstant(
                                now.getZone()
                        );

        Long remainingTimeSeconds =
                calculateRemainingTimeSeconds(
                        space.getType(),
                        occupancyExpiresAt,
                        now
                );

        return new SpaceListItem(
                space.getId(),
                space.getName(),
                space.getType(),
                space.getCapacity(),
                status,
                occupancyExpiresAt,
                remainingTimeSeconds
        );
    }

    private Long calculateRemainingTimeSeconds(
            SpaceType type,
            ZonedDateTime expiresAt,
            ZonedDateTime now
    ) {
        if (type != SpaceType.MEETING) {
            return null;
        }

        if (expiresAt == null) {
            return null;
        }

        long remainingSeconds = Duration.between(
                now.toInstant(),
                expiresAt.toInstant()
        ).getSeconds();

        return Math.max(
                remainingSeconds,
                0L
        );
    }
}