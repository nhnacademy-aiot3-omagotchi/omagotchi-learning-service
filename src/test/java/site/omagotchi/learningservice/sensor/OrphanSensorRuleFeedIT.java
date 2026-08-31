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
import site.omagotchi.learningservice.sensor.application.ThresholdRuleService;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주인 없는 센서의 룰이 Rule Engine 으로 나가지 않는지 본다.
 *
 * <p><b>{@code active} 플래그에 기대면 안 되는 이유를 고정한다.</b> 기수 종료 정리는 되돌릴
 * 수단이 없는 한 번뿐인 훅이고, 센서 비활성화가 롤백되면 플래그는 {@code true} 로 남는다.
 * 그 상태에서 공간 해제가 진행되면 센서는 기수로도 공간으로도 찾을 수 없다 — 그때도 룰은
 * 멈춰야 한다. 적재 기준이 플래그로 되돌아가면 이 테스트가 실패한다.</p>
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("고아 센서 룰 적재")
class OrphanSensorRuleFeedIT {

    private static final long COHORT_ID = 9_300_001L;
    private static final long SPACE_ID = 9_300_011L;
    private static final UUID OWNER_ID = new UUID(0L, COHORT_ID);
    private static final String DEVICE_EUI = "9300011000000001";

    @Autowired
    private ThresholdRuleService thresholdRuleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohorts
                       (id, name, start_date, end_date, status, created_by_user_id)
                VALUES (?, '룰 적재 검증 기수', DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE', ?)
                """, COHORT_ID, OWNER_ID);
        jdbcTemplate.update("""
                INSERT INTO learning_service.spaces (id, name, space_type, cohort_id, capacity, status)
                VALUES (?, '룰 적재 검증 실습실', 'LAB', ?, 10, 'ACTIVE')
                """, SPACE_ID, COHORT_ID);
        // 회수되지 않은 상태 그대로 둔다 — active = TRUE
        jdbcTemplate.update("""
                INSERT INTO learning_service.sensor_devices
                       (device_eui, space_id, model, display_name, expected_interval_seconds, active)
                VALUES (?, ?, 'AM103', '검증 센서', 60, TRUE)
                """, DEVICE_EUI, SPACE_ID);
        jdbcTemplate.update("""
                INSERT INTO learning_service.threshold_rules (device_eui, metric, operator, threshold)
                VALUES (?, 'co2', 'GT', 1000)
                """, DEVICE_EUI);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("기수가 배정된 공간의 센서 룰은 적재된다")
    void feedsRulesOfSensorsInAssignedSpaces() {
        assertThat(euisFedToRuleEngine()).contains(DEVICE_EUI);
    }

    @Test
    @DisplayName("회수가 실패해 active로 남아도 고아가 되면 룰은 적재되지 않는다")
    void stopsFeedingRulesOnceSensorBecomesOrphanEvenIfStillActive() {
        // 기수 종료 정리가 센서 비활성화에 실패한 뒤 공간 해제만 진행된 상태
        jdbcTemplate.update(
                "UPDATE learning_service.spaces SET cohort_id = NULL WHERE id = ?", SPACE_ID);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM learning_service.sensor_devices WHERE device_eui = ?",
                Boolean.class, DEVICE_EUI))
                .as("회수 실패를 재현한다 — 플래그는 내려가지 않았다")
                .isTrue();
        assertThat(euisFedToRuleEngine()).doesNotContain(DEVICE_EUI);
    }

    @Test
    @DisplayName("공간이 소프트 삭제되어도 룰은 적재되지 않는다")
    void stopsFeedingRulesWhenSpaceIsSoftDeleted() {
        jdbcTemplate.update(
                "UPDATE learning_service.spaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                SPACE_ID);

        assertThat(euisFedToRuleEngine()).doesNotContain(DEVICE_EUI);
    }

    private List<String> euisFedToRuleEngine() {
        return thresholdRuleService.readAllForRuleEngine().stream()
                .map(ThresholdRule::getDeviceEui)
                .toList();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM learning_service.threshold_rules WHERE device_eui = ?", DEVICE_EUI);
        jdbcTemplate.update("DELETE FROM learning_service.sensor_devices WHERE device_eui = ?", DEVICE_EUI);
        jdbcTemplate.update("DELETE FROM learning_service.spaces WHERE id = ?", SPACE_ID);
        jdbcTemplate.update("DELETE FROM learning_service.cohorts WHERE id = ?", COHORT_ID);
    }
}
