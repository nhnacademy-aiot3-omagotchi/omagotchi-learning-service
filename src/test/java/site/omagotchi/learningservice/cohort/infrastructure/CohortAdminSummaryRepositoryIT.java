package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, QueryDslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CohortAdminSummaryRepositoryIT {

    private static final UUID ADMIN_ID = new UUID(0L, 1L);
    private static final UUID MANAGER_ID = new UUID(0L, 10L);
    private static final UUID STUDENT_ID = new UUID(0L, 20L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CohortMembershipRepository membershipRepository;

    @Test
    void aggregatesOnlyActiveMembersAndActiveManagers() {
        Long cohortId = jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name, start_date, end_date, status, created_by_user_id
                ) VALUES ('AIoT 3기', DATE '2026-08-01', DATE '2026-12-18', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, ADMIN_ID);
        insertMembership(cohortId, MANAGER_ID, "MANAGER", "ACTIVE");
        insertMembership(cohortId, STUDENT_ID, "STUDENT", "ACTIVE");
        insertMembership(cohortId, new UUID(0L, 30L), "STUDENT", "REJECTED");

        assertThat(membershipRepository.countActiveMembershipsByCohort())
                .singleElement()
                .satisfies(count -> {
                    assertThat(count.getCohortId()).isEqualTo(cohortId);
                    assertThat(count.getMemberCount()).isEqualTo(2L);
                });
        assertThat(membershipRepository.findActiveManagersByCohort())
                .singleElement()
                .satisfies(manager -> {
                    assertThat(manager.getCohortId()).isEqualTo(cohortId);
                    assertThat(manager.getUserId()).isEqualTo(MANAGER_ID);
                });
    }

    private void insertMembership(Long cohortId, UUID userId, String role, String status) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id, user_id, role, status, processed_at,
                    processed_by_user_id, rejection_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                cohortId,
                userId,
                role,
                status,
                OffsetDateTime.now(),
                ADMIN_ID,
                "REJECTED".equals(status) ? "요청 거절" : null
        );
    }
}
