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
        Long cohortId = fixture.createCohort("실습실-동시성");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        Long firstLabId = fixture.createLab(cohortId, "실습실-동시성-A-" + cohortId, 20);
        Long secondLabId = fixture.createLab(cohortId, "실습실-동시성-B-" + cohortId, 20);
        cohortService.changeStatus(
                cohortId,
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                GlobalRole.SYSTEM_ADMIN
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> deactivate(
                    firstLabId,
                    manager,
                    ready,
                    start
            ));
            Future<Boolean> second = executor.submit(() -> deactivate(
                    secondLabId,
                    manager,
                    ready,
                    start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(activeLabCount(cohortId)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean deactivate(
            Long spaceId,
            OccupancyTestFixture.Member manager,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            spaceCommandService.deactivate(spaceId, "동시성 검증", manager.userId());
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode())
                    .isEqualTo(SpaceErrorCode.LAST_ACTIVE_LAB_REQUIRED);
            return false;
        }
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
}
