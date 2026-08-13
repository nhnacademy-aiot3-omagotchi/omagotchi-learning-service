package site.omagotchi.learningservice.cohort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기수 소속 종료 전이 (GR-16, MR-26의 진입점).
 *
 * <p>실제 PostgreSQL이 있어야 의미가 있다 — 이 전이의 계약 대부분이 DB CHECK다.
 * {@code ck_cohort_memberships_ended_at}은 {@code status='ENDED'}에 {@code ended_at}을
 * 함께 요구하고, {@code ck_cohort_memberships_processed}는 PENDING이 아닌 행에
 * {@code processed_at}·{@code processed_by_user_id}를 요구한다. 한쪽만 쓰면 커밋이 거부된다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class CohortMembershipEndIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    CohortMembershipService membershipService;

    @Autowired
    CohortMembershipQueryService membershipQueryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** 상태와 시각을 함께 써야 CHECK를 통과한다. 하나만 쓰면 여기서 커밋이 거부된다. */
    @Test
    @DisplayName("소속을 종료하면 상태와 종료 시각이 함께 기록된다")
    void endWritesStatusAndEndedAtTogether() {
        Long cohortId = fixture.createCohort("종료-전이");
        OccupancyTestFixture.Member member = fixture.createAbsentMember(cohortId);

        assertThat(membershipService.end(member.membershipId())).isTrue();

        assertThat(statusOf(member.membershipId())).isEqualTo("ENDED");
        assertThat(endedAtOf(member.membershipId())).isNotNull();
    }

    /**
     * 종료된 소속은 활성 조회에서 빠져야 한다. 여기가 새면 이미 나간 사람이 팀을 만들거나
     * 회의실을 점유할 수 있다 — 팀·점유의 모든 권한 판정이 이 조회를 거친다.
     */
    @Test
    @DisplayName("종료된 소속은 활성 멤버십 조회에서 빠진다")
    void endedMembershipDisappearsFromActiveLookup() {
        Long cohortId = fixture.createCohort("종료-조회");
        OccupancyTestFixture.Member member = fixture.createAbsentMember(cohortId);

        assertThat(membershipQueryService.findActiveMembership(cohortId, member.userId()))
                .isPresent();

        membershipService.end(member.membershipId());

        assertThat(membershipQueryService.findActiveMembership(cohortId, member.userId()))
                .isEmpty();
        assertThat(membershipQueryService.findActiveMembership(member.membershipId()))
                .isEmpty();
    }

    /**
     * 훅은 재전달된다. 두 번째 호출이 예외를 던지면 훅이 실패로 기록돼 무한 재시도에
     * 빠지고, {@code ended_at}을 덮어쓰면 "언제 끝났는지"가 재시도 시각으로 밀린다.
     */
    @Test
    @DisplayName("같은 소속을 두 번 종료해도 종료 시각이 바뀌지 않는다")
    void endIsIdempotentAndKeepsFirstEndedAt() {
        Long cohortId = fixture.createCohort("종료-멱등");
        OccupancyTestFixture.Member member = fixture.createAbsentMember(cohortId);

        assertThat(membershipService.end(member.membershipId())).isTrue();
        OffsetDateTime firstEndedAt = endedAtOf(member.membershipId());

        assertThat(membershipService.end(member.membershipId())).isFalse();

        assertThat(endedAtOf(member.membershipId())).isEqualTo(firstEndedAt);
    }

    private String statusOf(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.cohort_memberships WHERE id = ?
                """, String.class, membershipId);
    }

    private OffsetDateTime endedAtOf(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.cohort_memberships WHERE id = ?
                """, OffsetDateTime.class, membershipId);
    }
}
