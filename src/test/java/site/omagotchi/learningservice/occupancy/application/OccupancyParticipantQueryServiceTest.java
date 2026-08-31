package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OccupancyParticipantQueryServiceTest {

    private static final Long SPACE_ID = 1L;
    private static final Long OCCUPANCY_ID = 10L;
    private static final Long COHORT_ID = 20L;
    private static final Long OCCUPIER_MEMBERSHIP_ID = 30L;
    private static final UUID OCCUPIER_ID = UUID.randomUUID();
    private static final UUID AVAILABLE_ID = UUID.randomUUID();
    private static final UUID CURRENT_ID = UUID.randomUUID();
    private static final UUID OTHER_ROOM_ID = UUID.randomUUID();
    private static final UUID INACTIVE_ID = UUID.randomUUID();
    private static final UUID ABSENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Mock RoomOccupancyRepository occupancyRepository;
    @Mock OccupancyParticipantRepository participantRepository;
    @Mock CohortMembershipQueryService membershipQueryService;
    @Mock CohortAccessService cohortAccessService;
    @Mock AttendancePresenceQueryService presenceQueryService;
    @Mock IdentityAccountClient identityAccountClient;

    private OccupancyParticipantQueryService service;

    @BeforeEach
    void setUp() {
        service = new OccupancyParticipantQueryService(
                occupancyRepository,
                participantRepository,
                membershipQueryService,
                cohortAccessService,
                presenceQueryService,
                identityAccountClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("같은 기수 ACTIVE 재실 계정만 반환하고 참여 상태를 구분한다")
    void searchesEligibleCandidatesAndPreservesParticipationStatus() {
        RoomOccupancy occupancy = activeOccupancy(NOW.plusSeconds(600));
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID)).willReturn(Optional.of(occupancy));
        given(membershipQueryService.findActiveMembership(OCCUPIER_MEMBERSHIP_ID))
                .willReturn(Optional.of(membership(OCCUPIER_MEMBERSHIP_ID, OCCUPIER_ID)));

        List<IdentityAccountView> accounts = List.of(
                account(AVAILABLE_ID), account(CURRENT_ID), account(OTHER_ROOM_ID),
                new IdentityAccountView(INACTIVE_ID, "비활성 사용자", "inactive@example.com",
                        IdentityAccountState.DISABLED)
        );

        CohortMembershipView availableMembership = membership(101L, AVAILABLE_ID);
        CohortMembershipView currentMembership = membership(102L, CURRENT_ID);
        CohortMembershipView otherMembership = membership(103L, OTHER_ROOM_ID);
        CohortMembershipView inactiveMembership = membership(104L, INACTIVE_ID);
        CohortMembershipView absentMembership = membership(105L, ABSENT_ID);
        given(membershipQueryService.findActiveMemberships(COHORT_ID)).willReturn(List.of(
                availableMembership, currentMembership, otherMembership, inactiveMembership, absentMembership));
        given(presenceQueryService.findOpenPresences(
                new LinkedHashSet<>(List.of(
                        AVAILABLE_ID, CURRENT_ID, OTHER_ROOM_ID, INACTIVE_ID, ABSENT_ID))))
                .willReturn(Map.of(
                        AVAILABLE_ID, presence(availableMembership),
                        CURRENT_ID, presence(currentMembership),
                        OTHER_ROOM_ID, presence(otherMembership),
                        INACTIVE_ID, presence(inactiveMembership)
                ));
        given(identityAccountClient.search("검색어",
                List.of(AVAILABLE_ID, CURRENT_ID, OTHER_ROOM_ID, INACTIVE_ID))).willReturn(accounts);
        given(participantRepository.findActiveOccupancyIdsByUserIds(
                List.of(AVAILABLE_ID, CURRENT_ID, OTHER_ROOM_ID)))
                .willReturn(Map.of(CURRENT_ID, OCCUPANCY_ID, OTHER_ROOM_ID, 999L));

        var results = service.searchCandidates(SPACE_ID, "  검색어  ", OCCUPIER_ID);

        assertThat(results).extracting(result -> result.userId())
                .containsExactly(AVAILABLE_ID, CURRENT_ID, OTHER_ROOM_ID);
        assertThat(results).extracting(result -> result.status())
                .containsExactly(
                        ParticipantCandidateStatus.AVAILABLE,
                        ParticipantCandidateStatus.ALREADY_PARTICIPATING,
                        ParticipantCandidateStatus.PARTICIPATING_ELSEWHERE
                );
    }

    @Test
    @DisplayName("앞 20개 검색 일치 계정이 탈락해도 21번째 재실 후보를 Identity 검색 범위에 포함한다")
    void searchesWithinEligibleCandidateIdsBeforeApplyingSearchLimit() {
        RoomOccupancy occupancy = activeOccupancy(NOW.plusSeconds(600));
        UUID eligibleId = UUID.randomUUID();
        List<UUID> ineligibleIds = IntStream.range(0, 20)
                .mapToObj(ignored -> UUID.randomUUID())
                .toList();
        List<CohortMembershipView> memberships = new java.util.ArrayList<>();
        for (int index = 0; index < ineligibleIds.size(); index++) {
            memberships.add(membership(100L + index, ineligibleIds.get(index)));
        }
        CohortMembershipView eligibleMembership = membership(200L, eligibleId);
        memberships.add(eligibleMembership);

        given(occupancyRepository.findActiveBySpaceId(SPACE_ID)).willReturn(Optional.of(occupancy));
        given(membershipQueryService.findActiveMembership(OCCUPIER_MEMBERSHIP_ID))
                .willReturn(Optional.of(membership(OCCUPIER_MEMBERSHIP_ID, OCCUPIER_ID)));
        given(membershipQueryService.findActiveMemberships(COHORT_ID)).willReturn(memberships);
        given(presenceQueryService.findOpenPresences(new LinkedHashSet<>(memberships.stream()
                .map(CohortMembershipView::userId).toList())))
                .willReturn(Map.of(eligibleId, presence(eligibleMembership)));
        given(identityAccountClient.search("동일 이름", List.of(eligibleId)))
                .willReturn(List.of(account(eligibleId)));
        given(participantRepository.findActiveOccupancyIdsByUserIds(List.of(eligibleId)))
                .willReturn(Map.of());

        var results = service.searchCandidates(SPACE_ID, "동일 이름", OCCUPIER_ID);

        assertThat(results).extracting(result -> result.userId()).containsExactly(eligibleId);
    }

    @Test
    @DisplayName("점유자가 아니면 후보 검색을 차단한다")
    void rejectsCandidateSearchByNonOccupier() {
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID))
                .willReturn(Optional.of(activeOccupancy(NOW.plusSeconds(600))));

        assertError(OccupancyErrorCode.NOT_OCCUPIER,
                () -> service.searchCandidates(SPACE_ID, "검색어", UUID.randomUUID()));
        verify(identityAccountClient, never()).search(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    @DisplayName("만료된 점유의 후보 검색을 차단한다")
    void rejectsCandidateSearchForExpiredOccupancy() {
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID))
                .willReturn(Optional.of(activeOccupancy(NOW.minusSeconds(1))));

        assertError(OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> service.searchCandidates(SPACE_ID, "검색어", OCCUPIER_ID));
    }

    @Test
    @DisplayName("현재 참여자만 상세 목록을 조회할 수 있다")
    void allowsParticipantListOnlyToCurrentParticipant() {
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID))
                .willReturn(Optional.of(activeOccupancy(NOW.plusSeconds(600))));
        given(participantRepository.findActiveUserIdsByOccupancyIds(List.of(OCCUPANCY_ID)))
                .willReturn(Map.of(OCCUPANCY_ID, List.of(OCCUPIER_ID, CURRENT_ID)));
        given(identityAccountClient.findDisplayNames(List.of(OCCUPIER_ID, CURRENT_ID)))
                .willReturn(Map.of(OCCUPIER_ID, "점유자", CURRENT_ID, "참여자"));

        var results = service.getParticipants(SPACE_ID, CURRENT_ID);

        assertThat(results).extracting(result -> result.displayName())
                .containsExactly("점유자", "참여자");
        assertThat(results).extracting(result -> result.occupier())
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("회의에 참여하지 않은 사용자는 상세 목록 조회가 거부된다")
    void rejectsParticipantListForNonParticipant() {
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID))
                .willReturn(Optional.of(activeOccupancy(NOW.plusSeconds(600))));
        given(participantRepository.findActiveUserIdsByOccupancyIds(List.of(OCCUPANCY_ID)))
                .willReturn(Map.of(OCCUPANCY_ID, List.of(OCCUPIER_ID)));

        assertError(OccupancyErrorCode.PARTICIPANT_ACCESS_DENIED,
                () -> service.getParticipants(SPACE_ID, UUID.randomUUID()));
        verify(identityAccountClient, never()).findDisplayNames(List.of(OCCUPIER_ID));
    }

    @Test
    @DisplayName("점유자 기수 관리자는 기존 참여자 목록을 조회할 수 있다")
    void allowsOccupierCohortManagerToGetParticipants() {
        UUID managerId = UUID.randomUUID();
        given(occupancyRepository.findActiveBySpaceId(SPACE_ID))
                .willReturn(Optional.of(activeOccupancy(NOW.plusSeconds(600))));
        given(participantRepository.findActiveUserIdsByOccupancyIds(List.of(OCCUPANCY_ID)))
                .willReturn(Map.of(OCCUPANCY_ID, List.of(OCCUPIER_ID, CURRENT_ID)));
        given(membershipQueryService.findActiveMembership(OCCUPIER_MEMBERSHIP_ID))
                .willReturn(Optional.of(membership(OCCUPIER_MEMBERSHIP_ID, OCCUPIER_ID)));
        given(cohortAccessService.isManager(COHORT_ID, managerId)).willReturn(true);
        given(identityAccountClient.findDisplayNames(List.of(OCCUPIER_ID, CURRENT_ID)))
                .willReturn(Map.of(OCCUPIER_ID, "점유자", CURRENT_ID, "참여자"));

        var results = service.getParticipants(SPACE_ID, managerId);

        assertThat(results).extracting(result -> result.displayName())
                .containsExactly("점유자", "참여자");
    }

    private static RoomOccupancy activeOccupancy(Instant expiresAt) {
        RoomOccupancy occupancy = RoomOccupancy.start(
                SPACE_ID,
                OCCUPIER_MEMBERSHIP_ID,
                OCCUPIER_ID,
                OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(occupancy, "id", OCCUPANCY_ID);
        return occupancy;
    }

    private static IdentityAccountView account(UUID userId) {
        return new IdentityAccountView(
                userId, "사용자-" + userId.toString().substring(0, 4),
                userId + "@example.com", IdentityAccountState.ACTIVE);
    }

    private static CohortMembershipView membership(Long membershipId, UUID userId) {
        return new CohortMembershipView(membershipId, COHORT_ID, userId);
    }

    private static OpenPresenceView presence(CohortMembershipView membership) {
        return new OpenPresenceView(membership.membershipId(), NOW.minusSeconds(30));
    }

    private static void assertError(OccupancyErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
