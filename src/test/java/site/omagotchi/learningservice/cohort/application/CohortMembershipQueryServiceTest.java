package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 다른 Feature에 노출하는 기수 소속 조회.
 *
 * <p>이 계약이 지켜야 할 것은 둘이다 — 예외 대신 {@link Optional}을 돌려줄 것,
 * domain 객체가 아니라 {@link CohortMembershipView}를 돌려줄 것. 전자가 깨지면 호출부의
 * 오류 코드가 기수 파트의 404로 뭉개지고, 후자가 깨지면 남의 Feature가 기수 엔티티를
 * 직접 조작할 수 있게 된다.</p>
 */
@ExtendWith(MockitoExtension.class)
class CohortMembershipQueryServiceTest {

    private static final Long MEMBERSHIP_ID = 10L;
    private static final Long COHORT_ID = 3L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private CohortMembershipRepository membershipRepository;

    @InjectMocks
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Test
    @DisplayName("활성 소속을 찾으면 식별자만 담아 돌려준다.")
    void test1() {
        given(membershipRepository.findByIdAndStatus(MEMBERSHIP_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.of(activeMembership()));

        Optional<CohortMembershipView> found =
                cohortMembershipQueryService.findActiveMembership(MEMBERSHIP_ID);

        assertThat(found).contains(new CohortMembershipView(MEMBERSHIP_ID, COHORT_ID, USER_ID));
    }

    /**
     * 예외가 아니라 빈 값인 것이 계약이다. 같은 "소속 없음"이 점유의 참여자 추가에서는
     * 400, 팀 생성에서는 403이라 판단은 호출부가 한다.
     */
    @Test
    @DisplayName("활성 소속이 없으면 예외 대신 빈 값을 돌려준다.")
    void test2() {
        given(membershipRepository.findByIdAndStatus(MEMBERSHIP_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThat(cohortMembershipQueryService.findActiveMembership(MEMBERSHIP_ID)).isEmpty();
    }

    /** ACTIVE로 좁히지 않으면 종료된 기수의 소속으로도 기수가 도출되어 정합 검증이 뚫린다. */
    @Test
    @DisplayName("조회는 ACTIVE 상태로 좁혀 위임한다.")
    void test3() {
        given(membershipRepository.findByIdAndStatus(MEMBERSHIP_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.empty());

        cohortMembershipQueryService.findActiveMembership(MEMBERSHIP_ID);

        verify(membershipRepository).findByIdAndStatus(MEMBERSHIP_ID, CohortMembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("멤버십 식별자가 없으면 조회하지 않는다.")
    void test4() {
        assertThat(cohortMembershipQueryService.findActiveMembership(null)).isEmpty();

        verify(membershipRepository, never())
                .findByIdAndStatus(null, CohortMembershipStatus.ACTIVE);
    }

    private CohortMembership activeMembership() {
        CohortMembership membership =
                CohortMembership.activeManager(COHORT_ID, USER_ID, UUID.randomUUID());
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        return membership;
    }
}
