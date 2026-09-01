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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.command.CreateSensorDeviceCommand;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 센서 배치가 공간 삭제와 같은 행 잠금으로 직렬화되는지 본다.
 *
 * <p>공간 삭제는 소프트 삭제라 공간 행이 남는다 — FK 로는 "삭제된 공간에 센서 저장"을 막지
 * 못한다. 삭제가 공간 행을 잠근 사이 센서 배치가 그대로 진행되면, 삭제 트랜잭션이 "센서 0대"를
 * 확인한 뒤 센서가 들어와 어느 기수에서도 보이지 않는 센서가 남는다.</p>
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("센서 배치 공간 잠금")
class SensorPlacementSpaceLockIT {

    private static final long COHORT_ID = 9_200_001L;
    private static final long SPACE_ID = 9_200_011L;
    private static final UUID MANAGER_ID = new UUID(0L, COHORT_ID);
    private static final String DEVICE_EUI = "9200011000000001";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private SensorDeviceService sensorDeviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void seed() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohorts
                       (id, name, start_date, end_date, status, created_by_user_id)
                VALUES (?, '잠금 검증 기수', DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE', ?)
                """, COHORT_ID, MANAGER_ID);
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships
                       (cohort_id, user_id, role, status, processed_at, processed_by_user_id)
                VALUES (?, ?, 'MANAGER', 'ACTIVE', CURRENT_TIMESTAMP, ?)
                """, COHORT_ID, MANAGER_ID, MANAGER_ID);
        jdbcTemplate.update("""
                INSERT INTO learning_service.spaces (id, name, space_type, cohort_id, capacity, status)
                VALUES (?, '잠금 검증 실습실', 'LAB', ?, 10, 'ACTIVE')
                """, SPACE_ID, COHORT_ID);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        cleanUp();
    }

    /**
     * 대기만 검증하면 안 된다 — FK INSERT 는 부모 행에 FOR KEY SHARE 를 잡으므로 잠금이
     * 없어도 어차피 대기한다. 갈리는 것은 <b>기다린 뒤의 결과</b>다. 잠그지 않으면 삭제 전
     * 스냅샷을 읽고 통과한 뒤 INSERT 만 대기했다가 성공해, 삭제된 공간에 센서가 남는다.
     */
    @Test
    @DisplayName("공간이 삭제되는 중에 등록하면 저장되지 않는다")
    void rejectsSensorCreationWhenSpaceIsBeingDeleted() throws Exception {
        CountDownLatch spaceLocked = new CountDownLatch(1);
        CountDownLatch releaseDeleter = new CountDownLatch(1);

        // 삭제 트랜잭션을 흉내낸다: 공간 행을 잠그고, 센서가 없음을 확인하고, 소프트 삭제한다
        Future<?> deleter = executor.submit(() -> new TransactionTemplate(transactionManager)
                .execute(status -> {
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM learning_service.spaces WHERE id = ? FOR UPDATE",
                            Long.class, SPACE_ID);
                    Long placed = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM learning_service.sensor_devices WHERE space_id = ?",
                            Long.class, SPACE_ID);
                    assertEquals(0L, placed, "삭제 시점에는 센서가 없어야 한다");

                    spaceLocked.countDown();
                    awaitQuietly(releaseDeleter);

                    jdbcTemplate.update(
                            "UPDATE learning_service.spaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                            SPACE_ID);
                    return null;
                }));
        assertTrue(spaceLocked.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        Future<?> creation = executor.submit(() -> sensorDeviceService.create(
                COHORT_ID, MANAGER_ID, createCommand()));

        // 등록이 실제로 잠금에 걸린 뒤에 풀어야 한다. 바로 풀면 등록이 시작하기도 전에
        // 삭제가 커밋되어, 잠금이 있든 없든 똑같이 "삭제된 공간"을 보고 실패한다.
        awaitBlockedOnLock();
        releaseDeleter.countDown();
        deleter.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        // 잠금이 없으면 여기서 성공한다 — 삭제된 공간에 센서가 남는 바로 그 경로다
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> creation.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        );
        assertInstanceOf(BusinessException.class, failure.getCause());

        Long stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_service.sensor_devices WHERE space_id = ?",
                Long.class, SPACE_ID);
        assertEquals(0L, stored, "삭제된 공간에 센서가 남으면 안 된다");
    }

    /** 누군가 잠금을 기다리기 시작할 때까지 폴링한다. */
    private void awaitBlockedOnLock() throws InterruptedException {
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("등록이 잠금에 걸리지 않았다 — 경합을 재현하지 못했다");
    }

    private CreateSensorDeviceCommand createCommand() {
        return new CreateSensorDeviceCommand(
                SPACE_ID, DEVICE_EUI, "AM103", "잠금 검증 센서", "창가", 60,
                Instant.parse("2026-08-30T00:00:00Z"));
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM learning_service.sensor_devices WHERE device_eui = ?", DEVICE_EUI);
        jdbcTemplate.update("DELETE FROM learning_service.spaces WHERE id = ?", SPACE_ID);
        jdbcTemplate.update("DELETE FROM learning_service.cohort_memberships WHERE cohort_id = ?", COHORT_ID);
        jdbcTemplate.update("DELETE FROM learning_service.cohorts WHERE id = ?", COHORT_ID);
    }
}
