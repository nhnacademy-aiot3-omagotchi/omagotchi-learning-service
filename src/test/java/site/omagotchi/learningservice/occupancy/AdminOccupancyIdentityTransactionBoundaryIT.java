package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.occupancy.application.AdminOccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class AdminOccupancyIdentityTransactionBoundaryIT {
    @Autowired OccupancyTestFixture fixture;
    @Autowired RoomOccupancyService occupancyService;
    @Autowired AdminOccupancyQueryService queryService;
    @Autowired CohortRepository cohortRepository;
    @MockitoSpyBean IdentityAccountClient identityAccountClient;

    @Test void adminOccupancyDisplayNameLookupRunsOutsideDatabaseTransaction() {
        Long cohortId = fixture.createCohort("관리자 점유 경계");
        var manager = fixture.createActiveMember(cohortId);
        var cohort = cohortRepository.findById(cohortId).orElseThrow();
        cohort.activate(true);
        cohortRepository.save(cohort);
        cohortRepository.flush();
        Long spaceId = fixture.createMeetingRoom(cohortId, "관리자 회의실", 4);
        occupancyService.start(spaceId, manager.userId());

        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            UUID userId = invocation.<java.util.Collection<UUID>>getArgument(0).iterator().next();
            return Map.of(userId, "점유자");
        }).given(identityAccountClient).findDisplayNames(any());

        assertThat(queryService.getActiveOccupancies(manager.userId())).singleElement();
    }
}
