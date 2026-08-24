package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, QueryDslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("기수 관리자 운영 기간 중복 조회")
class CohortManagerPeriodRepositoryIT {

    private static final UUID MANAGER_ID = new UUID(0L, 10L);
    private static final UUID ADMIN_ID = new UUID(0L, 1L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CohortRepository cohortRepository;

    @Test
    @DisplayName("운영 기간이 일부라도 겹치면 충돌")
    void detectsOverlappingPeriod() {
        Long assignedCohortId = insertCohort("기존 기수", "2027-01-01", "2027-06-30", "ACTIVE");
        Long targetCohortId = insertCohort("대상 기수", "2027-06-29", "2027-12-31", "PREPARING");
        insertManager(assignedCohortId);

        assertThat(cohortRepository.existsActiveManagerPeriodConflict(
                MANAGER_ID,
                targetCohortId,
                LocalDate.of(2027, 6, 29),
                LocalDate.of(2027, 12, 31)
        )).isTrue();
    }

    @Test
    @DisplayName("기존 종료일과 다음 시작일이 같으면 전환 가능")
    void allowsTouchingPeriodBoundary() {
        Long assignedCohortId = insertCohort("기존 기수", "2027-01-01", "2027-06-30", "ACTIVE");
        Long targetCohortId = insertCohort("다음 기수", "2027-06-30", "2027-12-31", "PREPARING");
        insertManager(assignedCohortId);

        assertThat(cohortRepository.existsActiveManagerPeriodConflict(
                MANAGER_ID,
                targetCohortId,
                LocalDate.of(2027, 6, 30),
                LocalDate.of(2027, 12, 31)
        )).isFalse();
    }

    @Test
    @DisplayName("종료된 기수의 관리자 이력은 새 배치를 막지 않는다")
    void ignoresClosedCohort() {
        Long closedCohortId = insertCohort("종료 기수", "2027-01-01", "2027-06-30", "CLOSED");
        Long targetCohortId = insertCohort("대상 기수", "2027-03-01", "2027-12-31", "PREPARING");
        insertManager(closedCohortId);

        assertThat(cohortRepository.existsActiveManagerPeriodConflict(
                MANAGER_ID,
                targetCohortId,
                LocalDate.of(2027, 3, 1),
                LocalDate.of(2027, 12, 31)
        )).isFalse();
    }

    private Long insertCohort(String name, String startDate, String endDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name, start_date, end_date, status, created_by_user_id
                ) VALUES (?, CAST(? AS DATE), CAST(? AS DATE), ?, ?)
                RETURNING id
                """, Long.class, name, startDate, endDate, status, ADMIN_ID);
    }

    private void insertManager(Long cohortId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id, user_id, role, status, processed_at, processed_by_user_id
                ) VALUES (?, ?, 'MANAGER', 'ACTIVE', ?, ?)
                """, cohortId, MANAGER_ID, OffsetDateTime.now(), ADMIN_ID);
    }
}
