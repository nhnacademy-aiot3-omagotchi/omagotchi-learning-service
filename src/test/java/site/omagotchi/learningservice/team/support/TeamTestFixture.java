package site.omagotchi.learningservice.team.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 팀 테스트용 기수·멤버십 픽스처.
 * <p>
 * 프로덕션 코드는 파사드를 통해서만 cohort에 접근하지만, 테스트 픽스처는
 * 리포지토리를 직접 써도 된다 — 의존 방향 규칙은 src/main 에만 적용된다.
 * </p>
 */
@TestComponent
@RequiredArgsConstructor
public class TeamTestFixture {

    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository membershipRepository;

    public Long createCohort(String name) {
        Cohort cohort = Cohort.create(
                name,
                "테스트 기수",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                UUID.randomUUID()
        );
        return cohortRepository.save(cohort).getId();
    }

    /**
     * ACTIVE 멤버십을 만든다.
     *
     * CohortMembership에 승인 메서드가 없고 정적 팩토리가 pending / activeManager 뿐이라
     * activeManager를 쓴다. 역할이 MANAGER로 고정되지만 팀 로직은 role을 읽지 않는다
     * (명세 05 v3: 팀 생성·가입에 역할 제한 없음).
     */
    public Member createActiveMember(Long cohortId) {
        UUID userId = UUID.randomUUID();
        CohortMembership membership = CohortMembership.activeManager(
                cohortId, userId, UUID.randomUUID()
        );
        Long membershipId = membershipRepository.save(membership).getId();
        return new Member(membershipId, userId);
    }

    /**
     * 역할이 실제로 필요한 테스트용. 승인 메서드가 없어 리플렉션으로 상태를 올린다.
     * TODO: cohort 파트에 approve() 가 생기면 교체한다.
     */
    public Member createActiveMember(Long cohortId, CohortMembershipRole role) {
        UUID userId = UUID.randomUUID();
        CohortMembership membership = CohortMembership.pending(cohortId, userId, role);
        ReflectionTestUtils.setField(membership, "status", CohortMembershipStatus.ACTIVE);
        ReflectionTestUtils.setField(membership, "processedAt", OffsetDateTime.now());

        Long membershipId = membershipRepository.save(membership).getId();
        return new Member(membershipId, userId);
    }

    /** PENDING 멤버십. 승인 대기 중인 사람이 팀을 만들 수 없는지 검증할 때 쓴다. */
    public Member createPendingMember(Long cohortId, CohortMembershipRole role) {
        UUID userId = UUID.randomUUID();
        CohortMembership membership = CohortMembership.pending(cohortId, userId, role);
        Long membershipId = membershipRepository.save(membership).getId();
        return new Member(membershipId, userId);
    }

    public record Member(Long membershipId, UUID userId) { }
}
