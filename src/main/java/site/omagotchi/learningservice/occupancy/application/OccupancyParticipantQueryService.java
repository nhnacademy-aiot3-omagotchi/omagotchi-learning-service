package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.OccupancyParticipantResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupancyParticipantQueryService {

    private static final int MAX_QUERY_LENGTH = 100;

    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final AttendancePresenceQueryService presenceQueryService;
    private final IdentityAccountClient identityAccountClient;
    private final Clock clock;

    public List<ParticipantCandidateResult> searchCandidates(
            Long spaceId,
            String query,
            UUID requesterUserId
    ) {
        RoomOccupancy occupancy = requireCurrentOccupancy(spaceId);
        if (!occupancy.getOccupierUserId().equals(requesterUserId)) {
            throw new BusinessException(OccupancyErrorCode.NOT_OCCUPIER);
        }

        String normalizedQuery = normalizeQuery(query);
        Long cohortId = requireOccupierMembership(occupancy).cohortId();
        List<IdentityAccountView> accounts = identityAccountClient.search(normalizedQuery).stream()
                .filter(account -> account.status() == IdentityAccountState.ACTIVE)
                .toList();
        if (accounts.isEmpty()) {
            return List.of();
        }

        List<UUID> accountIds = accounts.stream().map(IdentityAccountView::accountId).toList();
        Map<UUID, CohortMembershipView> memberships =
                cohortMembershipQueryService.findActiveMemberships(cohortId, accountIds);
        Map<UUID, OpenPresenceView> presences = presenceQueryService.findOpenPresences(accountIds);
        Map<UUID, Long> activeOccupancies =
                participantRepository.findActiveOccupancyIdsByUserIds(accountIds);

        return accounts.stream()
                .filter(account -> isPresentThroughActiveMembership(
                        memberships.get(account.accountId()),
                        presences.get(account.accountId())
                ))
                .map(account -> new ParticipantCandidateResult(
                        account.accountId(),
                        account.displayName(),
                        account.email(),
                        candidateStatus(occupancy.getId(), activeOccupancies.get(account.accountId()))
                ))
                .toList();
    }

    public List<OccupancyParticipantResult> getParticipants(Long spaceId, UUID requesterUserId) {
        RoomOccupancy occupancy = requireCurrentOccupancy(spaceId);
        List<UUID> participantIds = participantRepository
                .findActiveUserIdsByOccupancyIds(List.of(occupancy.getId()))
                .getOrDefault(occupancy.getId(), List.of());
        if (!participantIds.contains(requesterUserId)) {
            throw new BusinessException(OccupancyErrorCode.PARTICIPANT_ACCESS_DENIED);
        }

        Map<UUID, String> displayNames = identityAccountClient.findDisplayNames(participantIds);
        return participantIds.stream()
                .map(userId -> new OccupancyParticipantResult(
                        userId,
                        displayNames.getOrDefault(userId, maskedLabel(userId)),
                        occupancy.getOccupierUserId().equals(userId)
                ))
                .toList();
    }

    private RoomOccupancy requireCurrentOccupancy(Long spaceId) {
        RoomOccupancy occupancy = occupancyRepository.findActiveBySpaceId(spaceId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));
        if (!occupancy.isActive() || occupancy.isExpiredAt(OffsetDateTime.now(clock))) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }
        return occupancy;
    }

    private CohortMembershipView requireOccupierMembership(RoomOccupancy occupancy) {
        return cohortMembershipQueryService.findActiveMembership(occupancy.getOccupierMembershipId())
                .orElseThrow(() -> new BusinessException(
                        OccupancyErrorCode.OCCUPIER_MEMBERSHIP_INACTIVE));
    }

    private static String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank() || normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private static boolean isPresentThroughActiveMembership(
            CohortMembershipView membership,
            OpenPresenceView presence
    ) {
        return membership != null
                && presence != null
                && membership.membershipId().equals(presence.cohortMembershipId());
    }

    private static ParticipantCandidateStatus candidateStatus(
            Long occupancyId,
            Long activeOccupancyId
    ) {
        if (activeOccupancyId == null) {
            return ParticipantCandidateStatus.AVAILABLE;
        }
        return occupancyId.equals(activeOccupancyId)
                ? ParticipantCandidateStatus.ALREADY_PARTICIPATING
                : ParticipantCandidateStatus.PARTICIPATING_ELSEWHERE;
    }

    private static String maskedLabel(UUID userId) {
        return "사용자 " + userId.toString().substring(0, 8);
    }
}
