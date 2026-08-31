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
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 관리자 화면의 "기수 운영 권한" 조회 Query 검증
 *
 * JPQL의 명시적 join과 Projection 이름 매핑은 애플리케이션 기동·실행 시점에만 깨지므로
 * 실제 DB에 대고 확인한다.
 */
@Import({TestcontainersConfiguration.class, QueryDslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("사용자별 기수 운영 권한 일괄 조회")
class CohortManagerAssignmentRepositoryIT {

    private static final UUID ADMIN_ID = new UUID(0L, 1L);
    private static final UUID MANAGER_ID = new UUID(0L, 10L);
    private static final UUID STUDENT_ID = new UUID(0L, 11L);
    private static final UUID OUTSIDER_ID = new UUID(0L, 12L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CohortMembershipRepository membershipRepository;

    @Test
    @DisplayName("활성 MANAGER 소속만 기수 이름과 함께 반환")
    void returnsActiveManagerAssignmentsWithCohortName() {
        Long firstCohortId = insertCohort("1기", "2027-01-01", "2027-06-30", "ACTIVE");
        Long secondCohortId = insertCohort("2기", "2027-07-01", "2027-12-31", "PREPARING");
        insertMembership(firstCohortId, MANAGER_ID, "MANAGER", "ACTIVE");
        insertMembership(secondCohortId, MANAGER_ID, "MANAGER", "ACTIVE");

        List<CohortMembershipRepository.CohortManagerAssignmentProjection> assignments =
                membershipRepository.findActiveManagerAssignments(List.of(MANAGER_ID));

        assertThat(assignments).hasSize(2);
        assertThat(assignments)
                .extracting(
                        CohortMembershipRepository.CohortManagerAssignmentProjection::getUserId,
                        CohortMembershipRepository.CohortManagerAssignmentProjection::getCohortId,
                        CohortMembershipRepository.CohortManagerAssignmentProjection::getCohortName,
                        CohortMembershipRepository.CohortManagerAssignmentProjection::getRole
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                MANAGER_ID, firstCohortId, "1기", CohortMembershipRole.MANAGER),
                        org.assertj.core.groups.Tuple.tuple(
                                MANAGER_ID, secondCohortId, "2기", CohortMembershipRole.MANAGER)
                );
    }

    @Test
    @DisplayName("STUDENT 소속은 운영 권한이 아니므로 제외")
    void excludesStudentMembership() {
        Long cohortId = insertCohort("1기", "2027-01-01", "2027-06-30", "ACTIVE");
        insertMembership(cohortId, STUDENT_ID, "STUDENT", "ACTIVE");

        assertThat(membershipRepository.findActiveManagerAssignments(List.of(STUDENT_ID)))
                .isEmpty();
    }

    @Test
    @DisplayName("종료된 MANAGER 소속은 제외")
    void excludesEndedManagerMembership() {
        Long cohortId = insertCohort("1기", "2027-01-01", "2027-06-30", "CLOSED");
        insertEndedMembership(cohortId, MANAGER_ID);

        assertThat(membershipRepository.findActiveManagerAssignments(List.of(MANAGER_ID)))
                .isEmpty();
    }

    @Test
    @DisplayName("운영 권한이 없는 사용자는 결과에서 빠짐")
    void omitsUsersWithoutAssignment() {
        Long cohortId = insertCohort("1기", "2027-01-01", "2027-06-30", "ACTIVE");
        insertMembership(cohortId, MANAGER_ID, "MANAGER", "ACTIVE");

        List<CohortMembershipRepository.CohortManagerAssignmentProjection> assignments =
                membershipRepository.findActiveManagerAssignments(
                        List.of(MANAGER_ID, OUTSIDER_ID));

        assertThat(assignments)
                .extracting(CohortMembershipRepository.CohortManagerAssignmentProjection::getUserId)
                .containsExactly(MANAGER_ID);
    }

    private Long insertCohort(String name, String startDate, String endDate, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name, start_date, end_date, status, created_by_user_id
                ) VALUES (?, CAST(? AS DATE), CAST(? AS DATE), ?, ?)
                RETURNING id
                """, Long.class, name, startDate, endDate, status, ADMIN_ID);
    }

    private void insertMembership(Long cohortId, UUID userId, String role, String status) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id, user_id, role, status, processed_at, processed_by_user_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, cohortId, userId, role, status, OffsetDateTime.now(), ADMIN_ID);
    }

    private void insertEndedMembership(Long cohortId, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id, user_id, role, status, processed_at, processed_by_user_id, ended_at
                ) VALUES (?, ?, 'MANAGER', 'ENDED', ?, ?, ?)
                """, cohortId, userId, OffsetDateTime.now(), ADMIN_ID, OffsetDateTime.now());
    }
}
