package site.omagotchi.learningservice.sensor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 기수가 같은 고아 센서를 동시에 인계할 때 정확히 하나만 성공하는지 본다.
 *
 * <p><b>단위 테스트로는 증명할 수 없다.</b> SensorDevice 에는 {@code @Version} 이 없어서
 * 직렬화의 근거가 오직 조회 시점의 행 잠금이다. 잠금이 빠지면 둘 다 고아 판정을 통과하고
 * 나중 커밋이 이기는데, 진 쪽도 200 을 받으므로 어느 단계에서도 실패가 드러나지 않는다.</p>
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("센서 인계 동시성")
class SensorClaimConcurrencyIT {

    private static final long COHORT_A = 9_100_001L;
    private static final long COHORT_B = 9_100_002L;
    private static final long SPACE_A = 9_100_011L;
    private static final long SPACE_B = 9_100_012L;
    /** 기수 배정이 풀린 공간. 여기 붙은 센서가 고아다. */
    private static final long ORPHAN_SPACE = 9_100_013L;
    private static final UUID MANAGER_A = new UUID(0L, COHORT_A);
    private static final UUID MANAGER_B = new UUID(0L, COHORT_B);
    private static final String ORPHAN_EUI = "9100013000000001";

    @Autowired
    private SensorDeviceService sensorDeviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void seed() {
        cleanUp();
        insertCohort(COHORT_A, "동시성 A");
        insertCohort(COHORT_B, "동시성 B");
        insertManager(COHORT_A, MANAGER_A);
        insertManager(COHORT_B, MANAGER_B);
        insertSpace(SPACE_A, "동시성 실습실 A", COHORT_A);
        insertSpace(SPACE_B, "동시성 실습실 B", COHORT_B);
        // 기수가 배정되지 않은 공간 — 기수 종료 후의 상태다
        insertSpace(ORPHAN_SPACE, "동시성 미배정", null);
        jdbcTemplate.update("""
                INSERT INTO learning_service.sensor_devices
                       (device_eui, space_id, model, display_name, expected_interval_seconds, active)
                VALUES (?, ?, 'AM103', '고아 센서', 60, FALSE)
                """, ORPHAN_EUI, ORPHAN_SPACE);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        cleanUp();
    }

    @Test
    @DisplayName("같은 고아 센서를 두 기수가 동시에 인계하면 하나만 성공한다")
    void allowsOnlyOneCohortToClaimTheSameOrphanSensor() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> byA = executor.submit(claim(COHORT_A, MANAGER_A, SPACE_A, start));
        Future<Boolean> byB = executor.submit(claim(COHORT_B, MANAGER_B, SPACE_B, start));
        start.countDown();

        List<Boolean> results = List.of(
                byA.get(10, TimeUnit.SECONDS),
                byB.get(10, TimeUnit.SECONDS)
        );

        // 둘 다 true 면 진 쪽이 200 을 받고도 센서를 갖지 못한다 — 잠금이 빠진 상태다
        assertThat(results).containsExactlyInAnyOrder(true, false);

        // 이긴 쪽 공간 하나에만 붙어 있고, 다시 운영 상태로 올라와 있다
        Long spaceId = jdbcTemplate.queryForObject(
                "SELECT space_id FROM learning_service.sensor_devices WHERE device_eui = ?",
                Long.class, ORPHAN_EUI);
        assertThat(spaceId).isIn(SPACE_A, SPACE_B);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM learning_service.sensor_devices WHERE device_eui = ?",
                Boolean.class, ORPHAN_EUI)).isTrue();
    }

    /** 인계에 성공하면 true, 이미 남의 것이 되어 거절되면 false. */
    private Callable<Boolean> claim(long cohortId, UUID managerId, long spaceId, CountDownLatch start) {
        return () -> {
            start.await();
            try {
                sensorDeviceService.claim(cohortId, managerId, ORPHAN_EUI, spaceId);
                return true;
            } catch (RuntimeException expected) {
                // 진 쪽은 DEVICE_NOT_FOUND — 앞 트랜잭션이 커밋한 소유 상태를 보고 거절된다
                return false;
            }
        };
    }

    private void insertCohort(long cohortId, String name) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohorts
                       (id, name, start_date, end_date, status, created_by_user_id)
                VALUES (?, ?, DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE', ?)
                """, cohortId, name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    private void insertManager(long cohortId, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships
                       (cohort_id, user_id, role, status, processed_at, processed_by_user_id)
                VALUES (?, ?, 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP, ?)
                """, cohortId, userId, userId);
    }

    private void insertSpace(long spaceId, String name, Long cohortId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.spaces (id, name, space_type, cohort_id, capacity, status)
                VALUES (?, ?, 'LAB', ?, 10, 'ACTIVE')
                """, spaceId, name, cohortId);
    }

    private void cleanUp() {
        // 센서 인계가 필수 룰과 변경 이력을 함께 생성하므로 FK 의존성 역순으로 지운다.
        jdbcTemplate.update("""
                DELETE FROM learning_service.threshold_rule_histories
                WHERE rule_id IN (
                    SELECT id
                    FROM learning_service.threshold_rules
                    WHERE device_eui = ?
                )
                """, ORPHAN_EUI);
        jdbcTemplate.update(
                "DELETE FROM learning_service.threshold_rules WHERE device_eui = ?", ORPHAN_EUI);
        jdbcTemplate.update("DELETE FROM learning_service.sensor_devices WHERE device_eui = ?", ORPHAN_EUI);
        jdbcTemplate.update("DELETE FROM learning_service.spaces WHERE id IN (?, ?, ?)",
                SPACE_A, SPACE_B, ORPHAN_SPACE);
        jdbcTemplate.update("DELETE FROM learning_service.cohort_memberships WHERE cohort_id IN (?, ?)",
                COHORT_A, COHORT_B);
        jdbcTemplate.update("DELETE FROM learning_service.cohorts WHERE id IN (?, ?)", COHORT_A, COHORT_B);
    }
}
