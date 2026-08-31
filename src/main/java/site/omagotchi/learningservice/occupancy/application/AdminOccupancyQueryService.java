package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.occupancy.application.result.AdminActiveOccupancyResult;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceNameResult;
import site.omagotchi.learningservice.team.application.IdentityDisplayNameQueryService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOccupancyQueryService {

    private final SpaceQueryService spaceQueryService;
    private final OccupancyQueryService occupancyQueryService;
    private final CohortAccessService cohortAccessService;
    private final IdentityDisplayNameQueryService identityDisplayNameQueryService;
    private final Clock clock;

    public List<AdminActiveOccupancyResult> getActiveOccupancies(UUID requesterUserId) {
        Set<Long> managedCohortIds = Set.copyOf(
                cohortAccessService.findActiveManagedCohortIds(requesterUserId));
        if (managedCohortIds.isEmpty()) {
            return List.of();
        }

        List<SpaceNameResult> spaces = spaceQueryService.findAllSpaceNames();
        if (spaces.isEmpty()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<Long, SpaceOccupancyView> occupancies = occupancyQueryService.findActiveBySpaceIds(
                spaces.stream().map(SpaceNameResult::spaceId).toList(), now);
        Map<Long, String> spaceNamesById = spaces.stream()
                .collect(Collectors.toMap(SpaceNameResult::spaceId, SpaceNameResult::name));
        List<SpaceOccupancyView> visibleOccupancies = occupancies.values().stream()
                .filter(occupancy -> occupancy.occupierCohortId() != null)
                .filter(occupancy -> managedCohortIds.contains(occupancy.occupierCohortId()))
                .toList();

        Map<UUID, String> displayNames = identityDisplayNameQueryService.findDisplayNames(
                visibleOccupancies.stream().map(SpaceOccupancyView::occupierUserId).toList());

        return visibleOccupancies.stream()
                .map(occupancy -> new AdminActiveOccupancyResult(
                        occupancy.spaceId(),
                        spaceNamesById.get(occupancy.spaceId()),
                        occupancy.occupancyId(),
                        occupancy.occupierUserId(),
                        displayNames.getOrDefault(
                                occupancy.occupierUserId(), maskedLabel(occupancy.occupierUserId())),
                        occupancy.participantUserIds().size(),
                        occupancy.startedAt(),
                        occupancy.expiresAt(),
                        Math.max(java.time.Duration.between(now, occupancy.expiresAt()).getSeconds(), 0L),
                        OccupancyStatus.ACTIVE
                ))
                .toList();
    }

    private static String maskedLabel(UUID userId) {
        return "사용자 " + userId.toString().substring(0, 8);
    }
}
