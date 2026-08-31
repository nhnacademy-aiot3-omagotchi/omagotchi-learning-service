package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceNameResult;
import site.omagotchi.learningservice.team.application.IdentityDisplayNameQueryService;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminOccupancyQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID OCCUPIER_ID = UUID.randomUUID();
    private static final UUID PARTICIPANT_ID = UUID.randomUUID();

    @Mock SpaceQueryService spaceQueryService;
    @Mock OccupancyQueryService occupancyQueryService;
    @Mock CohortAccessService cohortAccessService;
    @Mock IdentityDisplayNameQueryService identityDisplayNameQueryService;

    private AdminOccupancyQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminOccupancyQueryService(
                spaceQueryService, occupancyQueryService, cohortAccessService,
                identityDisplayNameQueryService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("관리 기수의 활성 점유에 점유자 이름과 실제 참여자 수를 붙인다")
    void returnsManagedActiveOccupanciesWithDisplayNameAndParticipantCount() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        given(cohortAccessService.findActiveManagedCohortIds(MANAGER_ID)).willReturn(List.of(20L));
        given(spaceQueryService.findAllSpaceNames()).willReturn(List.of(new SpaceNameResult(1L, "회의실 A")));
        given(occupancyQueryService.findActiveBySpaceIds(List.of(1L), now))
                .willReturn(Map.of(1L, occupancy(1L, 10L, 20L, now)));
        given(identityDisplayNameQueryService.findDisplayNames(List.of(OCCUPIER_ID)))
                .willReturn(Map.of(OCCUPIER_ID, "점유자 이름"));

        var results = service.getActiveOccupancies(MANAGER_ID);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.spaceName()).isEqualTo("회의실 A");
            assertThat(result.occupancyId()).isEqualTo(10L);
            assertThat(result.occupierDisplayName()).isEqualTo("점유자 이름");
            assertThat(result.participantCount()).isEqualTo(2);
            assertThat(result.remainingTimeSeconds()).isEqualTo(600L);
            assertThat(result.status()).isEqualTo(OccupancyStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("관리하지 않는 타 기수 점유는 노출하지 않는다")
    void excludesOccupanciesOfOtherCohorts() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        given(cohortAccessService.findActiveManagedCohortIds(MANAGER_ID)).willReturn(List.of(20L));
        given(spaceQueryService.findAllSpaceNames()).willReturn(List.of(new SpaceNameResult(1L, "회의실 A")));
        given(occupancyQueryService.findActiveBySpaceIds(List.of(1L), now))
                .willReturn(Map.of(1L, occupancy(1L, 10L, 99L, now)));
        given(identityDisplayNameQueryService.findDisplayNames(List.of())).willReturn(Map.of());

        assertThat(service.getActiveOccupancies(MANAGER_ID)).isEmpty();
    }

    private static SpaceOccupancyView occupancy(
            Long spaceId, Long occupancyId, Long cohortId, OffsetDateTime now) {
        return new SpaceOccupancyView(
                occupancyId, spaceId, now.minusSeconds(60), now.plusSeconds(600), cohortId,
                30L, OCCUPIER_ID, List.of(OCCUPIER_ID, PARTICIPANT_ID));
    }

}
