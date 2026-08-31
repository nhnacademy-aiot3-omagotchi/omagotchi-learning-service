package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantQueryService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyParticipantIdentityTransactionBoundaryIT {
    @Autowired OccupancyTestFixture fixture;
    @Autowired RoomOccupancyService occupancyService;
    @Autowired OccupancyParticipantQueryService queryService;
    @MockitoSpyBean IdentityAccountClient identityAccountClient;

    @Test void candidateSearchRunsIdentityOutsideDatabaseTransaction() {
        Long cohortId = fixture.createCohort("후보 경계");
        var occupier = fixture.createActiveMember(cohortId);
        var candidate = fixture.createActiveMember(cohortId);
        Long spaceId = fixture.createMeetingRoom(cohortId, "후보 경계 회의실", 4);
        occupancyService.start(spaceId, occupier.userId());
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(new IdentityAccountView(candidate.userId(), "후보", "candidate@example.com", IdentityAccountState.ACTIVE));
        }).given(identityAccountClient).search(any(), any());
        assertThat(queryService.searchCandidates(spaceId, "후보", occupier.userId())).singleElement();
    }

    @Test void participantDisplayNameLookupRunsIdentityOutsideDatabaseTransaction() {
        Long cohortId = fixture.createCohort("참여자 경계");
        var occupier = fixture.createActiveMember(cohortId);
        Long spaceId = fixture.createMeetingRoom(cohortId, "참여자 경계 회의실", 4);
        occupancyService.start(spaceId, occupier.userId());
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Collection<UUID> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, UUID::toString));
        }).given(identityAccountClient).findDisplayNames(any());
        assertThat(queryService.getParticipants(spaceId, occupier.userId())).singleElement();
    }
}
