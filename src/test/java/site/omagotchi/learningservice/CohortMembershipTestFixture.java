package site.omagotchi.learningservice;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

final class CohortMembershipTestFixture {

    private static final long COHORT_ID = 9_000_000L;
    private static final UUID SYSTEM_USER_ID = new UUID(0L, COHORT_ID);

    private CohortMembershipTestFixture() {
    }

    static void ensureActiveMemberships(
            JdbcTemplate jdbcTemplate,
            long... membershipIds
    ) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohorts (
                    id,
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id
                ) VALUES (?, '학습 영속성 테스트 기수', DATE '2000-01-01', DATE '2099-12-31', 'ACTIVE', ?)
                ON CONFLICT (id) DO NOTHING
                """, COHORT_ID, SYSTEM_USER_ID);

        for (long membershipId : membershipIds) {
            UUID userId = new UUID(0L, membershipId);
            jdbcTemplate.update("""
                    INSERT INTO learning_service.cohort_memberships (
                        id,
                        cohort_id,
                        user_id,
                        role,
                        status,
                        processed_at,
                        processed_by_user_id
                    ) VALUES (?, ?, ?, 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, membershipId, COHORT_ID, userId, SYSTEM_USER_ID);
        }
    }
}
