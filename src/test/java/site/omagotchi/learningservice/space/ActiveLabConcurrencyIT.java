package site.omagotchi.learningservice.space;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
@DisplayName("활성 기수의 마지막 실습실 동시성 보호")
class ActiveLabConcurrencyIT {

    @Autowired
    private OccupancyTestFixture fixture;

    @Autowired
    private CohortService cohortService;

    @Autowired
    private SpaceCommandService spaceCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private VacancyAlertSender vacancyAlertSender;

    @Test
    @DisplayName("활성 실습실 두 개를 동시에 비활성화해도 하나는 반드시 남는다")
    void keepsOneActiveLabWhenTwoLabsAreDeactivatedConcurrently() throws Exception {
        Scenario scenario = createScenario("동시-비활성화");

        List<ReductionResult> results = executeConcurrently(
                scenario,
                new ReductionRequest(scenario.firstLabId(), ReductionAction.DEACTIVATE),
                new ReductionRequest(scenario.secondLabId(), ReductionAction.DEACTIVATE)
        );

        assertOneCommittedAndOneRejected(results);
        ReductionResult committed = committedResult(results);
        ReductionResult rejected = rejectedResult(results);
        assertSpaceState(committed.spaceId(), scenario.cohortId(), "INACTIVE");
        assertSpaceState(rejected.spaceId(), scenario.cohortId(), "ACTIVE");
        assertThat(activeLabCount(scenario.cohortId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("활성 실습실 두 개의 기수 배정을 동시에 해제해도 하나는 반드시 기수에 남는다")
    void keepsOneAssignedActiveLabWhenTwoLabsAreUnassignedConcurrently() throws Exception {
        Scenario scenario = createScenario("동시-배정해제");

        List<ReductionResult> results = executeConcurrently(
                scenario,
                new ReductionRequest(scenario.firstLabId(), ReductionAction.UNASSIGN),
                new ReductionRequest(scenario.secondLabId(), ReductionAction.UNASSIGN)
        );

        assertOneCommittedAndOneRejected(results);
        ReductionResult committed = committedResult(results);
        ReductionResult rejected = rejectedResult(results);
        assertSpaceState(committed.spaceId(), null, "ACTIVE");
        assertSpaceState(rejected.spaceId(), scenario.cohortId(), "ACTIVE");
        assertThat(activeLabCount(scenario.cohortId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("비활성화와 기수 배정 해제를 동시에 요청해도 활성 실습실 하나는 기수에 남는다")
    void keepsOneAssignedActiveLabAcrossMixedReductionCommands() throws Exception {
        Scenario scenario = createScenario("동시-혼합감소");

        List<ReductionResult> results = executeConcurrently(
                scenario,
                new ReductionRequest(scenario.firstLabId(), ReductionAction.DEACTIVATE),
                new ReductionRequest(scenario.secondLabId(), ReductionAction.UNASSIGN)
        );

        assertOneCommittedAndOneRejected(results);
        ReductionResult committed = committedResult(results);
        ReductionResult rejected = rejectedResult(results);
        if (committed.action() == ReductionAction.DEACTIVATE) {
            assertSpaceState(committed.spaceId(), scenario.cohortId(), "INACTIVE");
        } else {
            assertSpaceState(committed.spaceId(), null, "ACTIVE");
        }
        assertSpaceState(rejected.spaceId(), scenario.cohortId(), "ACTIVE");
        assertThat(activeLabCount(scenario.cohortId())).isEqualTo(1L);
    }

    private Scenario createScenario(String name) {
        Long cohortId = fixture.createCohort("실습실-" + name);
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        Long firstLabId = fixture.createLab(cohortId, name + "-A-" + cohortId, 20);
        Long secondLabId = fixture.createLab(cohortId, name + "-B-" + cohortId, 20);
        cohortService.changeStatus(
                cohortId,
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                GlobalRole.SYSTEM_ADMIN
        );
        return new Scenario(cohortId, manager, firstLabId, secondLabId);
    }

    private List<ReductionResult> executeConcurrently(
            Scenario scenario,
            ReductionRequest firstRequest,
            ReductionRequest secondRequest
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReductionResult> first = executor.submit(() -> reduce(
                    firstRequest,
                    scenario.manager(),
                    ready,
                    start
            ));
            Future<ReductionResult> second = executor.submit(() -> reduce(
                    secondRequest,
                    scenario.manager(),
                    ready,
                    start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private ReductionResult reduce(
            ReductionRequest request,
            OccupancyTestFixture.Member manager,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 시작 신호를 받지 못했습니다.");
        }
        try {
            if (request.action() == ReductionAction.DEACTIVATE) {
                spaceCommandService.deactivate(
                        request.spaceId(),
                        "동시성 검증",
                        manager.userId()
                );
            } else {
                spaceCommandService.unassignCohort(request.spaceId(), manager.userId());
            }
            return new ReductionResult(request.spaceId(), request.action(), true);
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode())
                    .isEqualTo(SpaceErrorCode.LAST_ACTIVE_LAB_REQUIRED);
            return new ReductionResult(request.spaceId(), request.action(), false);
        }
    }

    private void assertOneCommittedAndOneRejected(List<ReductionResult> results) {
        assertThat(results)
                .extracting(ReductionResult::committed)
                .containsExactlyInAnyOrder(true, false);
    }

    private ReductionResult committedResult(List<ReductionResult> results) {
        return results.stream()
                .filter(ReductionResult::committed)
                .findFirst()
                .orElseThrow();
    }

    private ReductionResult rejectedResult(List<ReductionResult> results) {
        return results.stream()
                .filter(result -> !result.committed())
                .findFirst()
                .orElseThrow();
    }

    private void assertSpaceState(
            Long spaceId,
            Long expectedCohortId,
            String expectedStatus
    ) {
        SpaceRow row = jdbcTemplate.queryForObject("""
                        SELECT cohort_id, status
                          FROM learning_service.spaces
                         WHERE id = ?
                        """,
                (resultSet, rowNumber) -> new SpaceRow(
                        resultSet.getObject("cohort_id", Long.class),
                        resultSet.getString("status")
                ),
                spaceId
        );

        assertThat(row).isNotNull();
        assertThat(row.cohortId()).isEqualTo(expectedCohortId);
        assertThat(row.status()).isEqualTo(expectedStatus);
    }

    private long activeLabCount(Long cohortId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                          FROM learning_service.spaces
                         WHERE cohort_id = ?
                           AND space_type = 'LAB'
                           AND status = 'ACTIVE'
                           AND deleted_at IS NULL
                        """,
                Long.class,
                cohortId
        );
        return count == null ? 0L : count;
    }

    private enum ReductionAction {
        DEACTIVATE,
        UNASSIGN
    }

    private record Scenario(
            Long cohortId,
            OccupancyTestFixture.Member manager,
            Long firstLabId,
            Long secondLabId
    ) {
    }

    private record ReductionRequest(Long spaceId, ReductionAction action) {
    }

    private record ReductionResult(
            Long spaceId,
            ReductionAction action,
            boolean committed
    ) {
    }

    private record SpaceRow(Long cohortId, String status) {
    }
}
