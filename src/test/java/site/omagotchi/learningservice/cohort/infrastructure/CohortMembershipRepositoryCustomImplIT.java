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
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QueryDSL 활성 학생 소속 조회")
class CohortMembershipRepositoryCustomImplIT {

    private static final UUID CREATED_BY_USER_ID = new UUID(0L, 1L);
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.parse(
            "2000-01-01T00:00:00Z"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CohortMembershipRepository membershipRepository;

    @Test
    @DisplayName("해당 기수의 종료되지 않은 ACTIVE STUDENT만 반환")
    void findsOnlyActiveStudentsOfCohort() {
        Long cohortId = insertCohort("조회 기수");
        Long otherCohortId = insertCohort("다른 기수");
        Long firstStudentId = insertMembership(cohortId, 10L, "STUDENT", "ACTIVE");
        Long secondStudentId = insertMembership(cohortId, 11L, "STUDENT", "ACTIVE");
        insertMembership(cohortId, 12L, "MENTOR", "ACTIVE");
        insertMembership(cohortId, 13L, "STUDENT", "ENDED");
        insertMembership(otherCohortId, 14L, "STUDENT", "ACTIVE");

        List<CohortMembership> students = membershipRepository.findActiveStudents(cohortId);

        assertEquals(
                List.of(firstStudentId, secondStudentId),
                students.stream().map(CohortMembership::getId).toList()
        );
    }

    private Long insertCohort(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id
                ) VALUES (?, DATE '2000-01-01', DATE '2000-12-31', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, name, CREATED_BY_USER_ID);
    }

    private Long insertMembership(
            Long cohortId,
            long userNumber,
            String role,
            String status
    ) {
        UUID userId = new UUID(0L, userNumber);
        OffsetDateTime endedAt = "ENDED".equals(status)
                ? PROCESSED_AT.plusDays(1)
                : null;
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    processed_at,
                    processed_by_user_id,
                    ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                cohortId,
                userId,
                role,
                status,
                PROCESSED_AT,
                userId,
                endedAt
        );
    }
}
