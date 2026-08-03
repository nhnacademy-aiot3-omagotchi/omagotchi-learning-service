package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceQueryPort;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository.ActiveOccupancyView;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository.ActiveParticipantView;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SpaceJpaQueryReader implements SpaceQueryPort {

    private final SpringDataSpaceRepository spaceRepository;

    private final SpringDataRoomOccupancyRepository
            roomOccupancyRepository;

    @Override
    public List<SpaceListResult> findAllSpacesWithStatus(
            Set<Long> requesterCohortIds,
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

        List<ActiveOccupancyView> occupancies =
                roomOccupancyRepository
                        .findAllActiveBySpaceIds(
                                spaceIds,
                                now.toOffsetDateTime()
                        );

        Map<Long, ActiveOccupancyView>
                occupancyBySpaceId =
                createOccupancyMap(occupancies);

        Map<Long, List<UUID>> participantsByOccupancyId =
                findParticipantsByOccupancyId(
                        occupancies,
                        requesterCohortIds
                );

        return spaces.stream()
                .map(space -> toListResult(
                        space,
                        occupancyBySpaceId.get(space.getId()),
                        requesterCohortIds,
                        participantsByOccupancyId,
                        now
                ))
                .toList();
    }

    private Map<Long, ActiveOccupancyView>
    createOccupancyMap(
            List<ActiveOccupancyView> occupancies
    ) {
        Map<Long, ActiveOccupancyView> result =
                new HashMap<>();

        /*
         * Repository에서 startedAt 내림차순으로 조회한다.
         *
         * 혹시 동일 공간에 활성 점유 데이터가 여러 건 있어도
         * 가장 최근 점유 하나만 사용한다.
         */
        for (ActiveOccupancyView occupancy : occupancies) {
            result.putIfAbsent(
                    occupancy.getSpaceId(),
                    occupancy
            );
        }

        return result;
    }

    private Map<Long, List<UUID>> findParticipantsByOccupancyId(
            List<ActiveOccupancyView> occupancies,
            Set<Long> requesterCohortIds
    ) {
        List<Long> occupancyIds = occupancies.stream()
                .filter(occupancy -> requesterCohortIds.contains(
                        occupancy.getOccupierCohortId()
                ))
                .map(ActiveOccupancyView::getId)
                .toList();

        if (occupancyIds.isEmpty()) {
            return Map.of();
        }

        return roomOccupancyRepository
                .findAllActiveParticipants(occupancyIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ActiveParticipantView::getOccupancyId,
                        Collectors.mapping(
                                ActiveParticipantView::getUserId,
                                Collectors.toList()
                        )
                ));
    }

    private SpaceListResult toListResult(
            SpaceJpaEntity space,
            ActiveOccupancyView occupancy,
            Set<Long> requesterCohortIds,
            Map<Long, List<UUID>> participantsByOccupancyId,
            ZonedDateTime now
    ) {
        SpaceUsageStatus status = determineUsageStatus(
                space,
                occupancy
        );

        ZonedDateTime occupancyExpiresAt =
                status != SpaceUsageStatus.OCCUPIED
                        ? null
                        : occupancy.getExpiresAt()
                        .atZone(now.getZone());

        Long remainingTimeSeconds =
                calculateRemainingTimeSeconds(
                        space.getSpaceType(),
                        occupancyExpiresAt,
                        now
                );

        boolean canViewOccupancyDetails =
                status == SpaceUsageStatus.OCCUPIED
                && requesterCohortIds.contains(
                        occupancy.getOccupierCohortId()
                );

        return new SpaceListResult(
                space.getId(),
                space.getName(),
                space.getSpaceType(),
                space.getCapacity(),
                space.getOperationalStatus(),
                space.getInactiveReason(),
                space.getCohortId(),
                status,
                occupancyExpiresAt,
                remainingTimeSeconds,
                canViewOccupancyDetails
                        ? occupancy.getOccupierCohortId()
                        : null,
                canViewOccupancyDetails
                        ? occupancy.getOccupierMembershipId()
                        : null,
                canViewOccupancyDetails
                        ? occupancy.getOccupierUserId()
                        : null,
                canViewOccupancyDetails
                        ? participantsByOccupancyId.getOrDefault(
                                occupancy.getId(),
                                List.of()
                        )
                        : null
        );
    }

    private SpaceUsageStatus determineUsageStatus(
            SpaceJpaEntity space,
            ActiveOccupancyView occupancy
    ) {
        if (space.getSpaceType() != SpaceType.MEETING) {
            return SpaceUsageStatus.NOT_APPLICABLE;
        }

        if (space.getOperationalStatus()
                == SpaceOperationalStatus.INACTIVE) {
            return SpaceUsageStatus.UNAVAILABLE;
        }

        return occupancy == null
                ? SpaceUsageStatus.AVAILABLE
                : SpaceUsageStatus.OCCUPIED;
    }

    private Long calculateRemainingTimeSeconds(
            SpaceType spaceType,
            ZonedDateTime expiresAt,
            ZonedDateTime now
    ) {
        if (spaceType != SpaceType.MEETING) {
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
