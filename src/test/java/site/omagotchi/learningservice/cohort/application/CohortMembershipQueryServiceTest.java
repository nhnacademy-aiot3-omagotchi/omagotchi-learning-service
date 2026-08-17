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
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("기수의 활성 학생 소속을 공개 조회 결과로 일괄 변환한다.")
    void findsActiveStudentMemberships() {
        given(membershipRepository.findActiveStudents(COHORT_ID))
                .willReturn(List.of(
                        studentMembership(MEMBERSHIP_ID, USER_ID),
                        studentMembership(20L, new UUID(0L, 2L))
                ));

        assertThat(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                .containsExactly(
                        new CohortMembershipView(MEMBERSHIP_ID, COHORT_ID, USER_ID),
                        new CohortMembershipView(20L, COHORT_ID, new UUID(0L, 2L))
                );
    }

    @Test
    @DisplayName("기수 식별자가 없으면 활성 학생을 조회하지 않는다.")
    void skipsActiveStudentQueryWithoutCohortId() {
        assertThat(cohortMembershipQueryService.findActiveStudentMemberships(null)).isEmpty();

        verify(membershipRepository, never()).findActiveStudents(any());
    }

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

    // ────────────────── 팀 파트 소비처 (기수 지정·전체·배치) ──────────────────

    @Test
    @DisplayName("기수와 계정으로 활성 소속을 찾는다.")
    void test5() {
        given(membershipRepository.findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                COHORT_ID, USER_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.of(activeMembership()));

        assertThat(cohortMembershipQueryService.findActiveMembership(COHORT_ID, USER_ID))
                .contains(new CohortMembershipView(MEMBERSHIP_ID, COHORT_ID, USER_ID));
    }

    /** 팀 생성은 "소속 없음"을 403으로 옮긴다 — 여기서 404를 던지면 그 구분이 무너진다. */
    @Test
    @DisplayName("해당 기수 소속이 없으면 예외 대신 빈 값을 돌려준다.")
    void test6() {
        given(membershipRepository.findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                COHORT_ID, USER_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThat(cohortMembershipQueryService.findActiveMembership(COHORT_ID, USER_ID)).isEmpty();
    }

    /** 매니저·멘토는 여러 기수를 담당할 수 있어 복수 건이 정상이다 (COH-F-17). */
    @Test
    @DisplayName("활성 소속 전체를 돌려준다.")
    void test7() {
        given(membershipRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
                .willReturn(List.of(activeMembership(), otherCohortMembership()));

        assertThat(cohortMembershipQueryService.findActiveMemberships(USER_ID))
                .containsExactly(
                        new CohortMembershipView(MEMBERSHIP_ID, COHORT_ID, USER_ID),
                        new CohortMembershipView(20L, 4L, USER_ID));
    }

    /**
     * 리포지토리 조회에 status 필터가 없어 서비스가 걸러야 한다. 이게 빠지면 종료·대기
     * 소속까지 세어 팀 생성이 "기수를 지정하라"(400)로 잘못 분기한다.
     */
    @Test
    @DisplayName("활성이 아닌 소속은 제외한다.")
    void test8() {
        CohortMembership pending = CohortMembership.pending(9L, USER_ID, CohortMembershipRole.STUDENT);
        ReflectionTestUtils.setField(pending, "id", 30L);
        given(membershipRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
                .willReturn(List.of(activeMembership(), pending));

        assertThat(cohortMembershipQueryService.findActiveMemberships(USER_ID))
                .containsExactly(new CohortMembershipView(MEMBERSHIP_ID, COHORT_ID, USER_ID));
    }

    /** 팀원이 몇 명이든 호출은 1회여야 한다 — 반복문 안 단건 조회는 N+1이 된다. */
    @Test
    @DisplayName("멤버십 식별자를 계정 식별자로 일괄 변환한다.")
    void test9() {
        given(membershipRepository.findAllById(List.of(MEMBERSHIP_ID, 20L)))
                .willReturn(List.of(activeMembership(), otherCohortMembership()));

        assertThat(cohortMembershipQueryService.findUserIds(List.of(MEMBERSHIP_ID, 20L)))
                .containsOnlyKeys(MEMBERSHIP_ID, 20L)
                .containsValue(USER_ID);
    }

    @Test
    @DisplayName("빈 목록이면 조회하지 않는다.")
    void test10() {
        assertThat(cohortMembershipQueryService.findUserIds(List.of())).isEmpty();

        verify(membershipRepository, never()).findAllById(any());
    }

    /**
     * 공간 목록의 점유자 기수 판정(MR-36)이 쓴다. 점유 행에 {@code cohort_id}가 없어
     * {@code occupier_membership_id}로 기수를 되찾아야 하는데, 방마다 단건 조회를 부르면
     * 그대로 N+1이 된다.
     */
    @Test
    @DisplayName("여러 멤버십의 기수를 한 번에 조회한다.")
    void test11() {
        given(membershipRepository.findAllById(List.of(MEMBERSHIP_ID, 20L)))
                .willReturn(List.of(activeMembership(), otherCohortMembership()));

        assertThat(cohortMembershipQueryService.findCohortIds(List.of(MEMBERSHIP_ID, 20L)))
                .containsEntry(MEMBERSHIP_ID, COHORT_ID)
                .containsEntry(20L, 4L);
    }

    /**
     * 상태로 좁히지 않는 것이 의도다. 이미 시작된 점유의 기수를 알아내는 용도라,
     * 그 사이 멤버십이 종료됐더라도 그 점유가 어느 기수의 것이었는지는 판정돼야 한다 —
     * 여기서 걸러내면 종료 직후의 점유가 모든 사용자에게 "남의 기수"로 보인다.
     */
    @Test
    @DisplayName("기수 조회는 멤버십 상태로 좁히지 않는다.")
    void test12() {
        CohortMembership ended = activeMembership();
        ReflectionTestUtils.setField(ended, "status", CohortMembershipStatus.ENDED);
        given(membershipRepository.findAllById(List.of(MEMBERSHIP_ID)))
                .willReturn(List.of(ended));

        assertThat(cohortMembershipQueryService.findCohortIds(List.of(MEMBERSHIP_ID)))
                .containsEntry(MEMBERSHIP_ID, COHORT_ID);
    }

    @Test
    @DisplayName("빈 목록이면 기수도 조회하지 않는다.")
    void test13() {
        assertThat(cohortMembershipQueryService.findCohortIds(List.of())).isEmpty();
        assertThat(cohortMembershipQueryService.findCohortIds(null)).isEmpty();

        verify(membershipRepository, never()).findAllById(any());
    }

    private CohortMembership otherCohortMembership() {
        CohortMembership membership =
                CohortMembership.activeManager(4L, USER_ID, UUID.randomUUID());
        ReflectionTestUtils.setField(membership, "id", 20L);
        return membership;
    }

    private CohortMembership studentMembership(Long membershipId, UUID userId) {
        CohortMembership membership = CohortMembership.pending(
                COHORT_ID,
                userId,
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(membership, "id", membershipId);
        ReflectionTestUtils.setField(membership, "status", CohortMembershipStatus.ACTIVE);
        return membership;
    }

    private CohortMembership activeMembership() {
        CohortMembership membership =
                CohortMembership.activeManager(COHORT_ID, USER_ID, UUID.randomUUID());
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        return membership;
    }
}
