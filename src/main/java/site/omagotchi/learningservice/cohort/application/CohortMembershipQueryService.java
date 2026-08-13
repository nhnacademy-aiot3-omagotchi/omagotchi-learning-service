package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 다른 Feature가 기수 소속을 조회하는 공개 계약.
 *
 * <p>{@link CohortAccessService}와 나눠 둔 이유는 역할이 다르기 때문이다. 저쪽은
 * "권한이 있는가"를 판정하고 없으면 기수 파트의 {@code ErrorCode}로 던진다. 여기는
 * 사실만 돌려주고 판단은 호출부에 맡긴다 — 같은 "소속 없음"이 파트마다 다른 코드이기
 * 때문이다 (점유의 참여자 추가는 400, 팀 생성은 403).</p>
 *
 * <p>그래서 예외를 던지지 않고 {@link Optional}을 반환한다. {@code require*}로 바꾸면
 * 호출부의 오류 코드가 전부 기수 파트의 404로 뭉개진다.</p>
 *
 * <p>반환은 domain의 {@code CohortMembership}이 아니라 {@link CohortMembershipView}다.
 * 상대 Feature가 domain 객체를 받으면 그것을 직접 조작할 수 있게 되고, 기수 파트가
 * 엔티티를 바꿀 때마다 남의 코드가 깨진다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortMembershipQueryService {

    private final CohortMembershipRepository membershipRepository;

    /**
     * 멤버십 식별자로 ACTIVE 소속을 조회한다.
     *
     * <p>점유의 참여자 기수 정합 검증(MR-33)이 첫 소비처다. 점유 행은 기수를 컬럼으로
     * 갖지 않고 {@code occupier_membership_id}만 보관하므로(ERD v3), "참여자의 기수 =
     * 점유자의 기수"를 확인하려면 멤버십에서 기수를 되찾아야 한다.</p>
     *
     * @return 종료·거절·대기 상태이거나 없는 멤버십이면 {@code Optional.empty()}
     */
    public Optional<CohortMembershipView> findActiveMembership(Long membershipId) {
        if (membershipId == null) {
            return Optional.empty();
        }
        return membershipRepository
                .findByIdAndStatus(membershipId, CohortMembershipStatus.ACTIVE)
                .map(CohortMembershipView::from);
    }

    /**
     * 특정 기수에서 이 계정의 ACTIVE 소속을 조회한다.
     *
     * <p>팀 생성 시 요청자 검증(RM-28)과 팀원 추가 시 대상 기수 정합 검증(GR-22)이
     * 소비처다. 후자는 조회 방향을 뒤집어 쓰는 것이 요점이다 — "팀의 기수로 대상을
     * 역조회"하면 "대상의 기수 == 팀의 기수"가 조회 결과로 자동 충족된다.</p>
     *
     * <p>{@link CohortAccessService#requireActiveMembership}과 결과는 같지만 실패 표현이
     * 다르다. 저쪽은 기수 존재를 숨기려 404를 던지고, 여기는 빈 값을 돌려준다 —
     * 같은 "소속 없음"이 팀 생성에서는 403이라 판단을 호출부에 남긴다.</p>
     */
    public Optional<CohortMembershipView> findActiveMembership(Long cohortId, UUID userId) {
        if (cohortId == null || userId == null) {
            return Optional.empty();
        }
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId, userId, CohortMembershipStatus.ACTIVE)
                .map(CohortMembershipView::from);
    }

    /**
     * 이 계정의 ACTIVE 소속 전체를 조회한다.
     *
     * <p>여러 건이 정상이다. 학생은 활성 멤버십이 하나지만 매니저·멘토는 여러 기수를
     * 동시에 담당할 수 있다 (COH-F-17). 팀 생성이 대상 기수를 단정할 수 없을 때
     * 이 개수로 분기하고(0개 403 / 2개 이상 기수 지정 요구), 내 팀 목록 조회는
     * 기수별로 하나씩 모은다.</p>
     *
     * <p>상태 필터를 리포지토리가 아니라 여기서 거는 것은 임시다 — 한 계정의 멤버십은
     * 많아야 기수 수만큼이라 인메모리로 충분하다. 규모가 커지면 쿼리로 내린다.</p>
     */
    public List<CohortMembershipView> findActiveMemberships(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return membershipRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .filter(membership -> membership.getStatus() == CohortMembershipStatus.ACTIVE)
                .map(CohortMembershipView::from)
                .toList();
    }

    /**
     * 멤버십 식별자를 계정 식별자로 일괄 변환한다.
     *
     * <p>팀원 목록의 표시명 조회 경로(GR-15) 첫 단계다. {@code team_members}는 멤버십
     * 식별자만 갖고 있어 Identity Service에 이름을 물으려면 계정 식별자로 바꿔야 한다.</p>
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 팀원이 8명이어도 호출은 1회여야 한다 —
     * 반복문 안에서 단건 조회를 부르면 기수 모듈이 분리될 때 그대로 N+1 원격 호출이 된다.</p>
     *
     * <p>상태로 좁히지 않는 것이 의도다. 이미 팀에 소속된 사람의 표시명을 찾는 용도라
     * 그 사이 멤버십이 종료됐더라도 이름은 보여줘야 한다.</p>
     */
    public Map<Long, UUID> findUserIds(Collection<Long> membershipIds) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findAllById(membershipIds).stream()
                .collect(Collectors.toMap(CohortMembership::getId, CohortMembership::getUserId));
    }

    /**
     * 멤버십 식별자를 기수 식별자로 일괄 변환한다.
     *
     * <p>공간 목록의 점유자 기수 판정(MR-36)이 첫 소비처다. 점유 행은 기수를 컬럼으로
     * 갖지 않고 {@code occupier_membership_id}만 보관하므로(ERD v3), "이 점유가 내 기수의
     * 것인가"를 판정하려면 멤버십에서 기수를 되찾아야 한다.</p>
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 공간이 N개여도 호출은 1회여야 한다 —
     * {@link #findActiveMembership(Long)}을 목록에서 반복하면 그대로 N+1이 되고,
     * 기수 모듈이 분리되면 N+1 원격 호출이 된다.</p>
     *
     * <p>{@link #findUserIds(Collection)}와 같은 이유로 상태를 좁히지 않는다. 이미 시작된
     * 점유의 기수를 알아내는 용도라, 그 사이 멤버십이 종료됐더라도 그 점유가 어느 기수의
     * 것이었는지는 그대로 판정해야 한다 — 여기서 걸러내면 종료 직후의 점유가 모든
     * 사용자에게 "남의 기수"로 보인다.</p>
     */
    public Map<Long, Long> findCohortIds(Collection<Long> membershipIds) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findAllById(membershipIds).stream()
                .collect(Collectors.toMap(CohortMembership::getId, CohortMembership::getCohortId));
    }

    /**
     * 이 중 더 이상 유효하지 않은 소속만 골라낸다.
     *
     * <p>후속 정리의 정합성 스윕이 소비처다 (ADR space-team/0013). 팀·점유는 소속을
     * {@code cohort_membership_id}로 키잡고 있어 "내 행이 가리키는 소속이 아직 살아 있나"를
     * 물어야 하는데, 그 판정을 각 파트가 {@code cohort_memberships}를 직접 조인해서 하면
     * 남의 테이블을 알게 된다. 여기서 답하면 소비처는 자기 테이블만 읽으면 된다.</p>
     *
     * <p><b>활성이 아닌 것을 돌려주지, 활성인 것을 돌려주고 여집합을 맡기지 않는다.</b>
     * 여집합 방식이면 이 조회가 실패하거나 빈 결과를 주는 순간 <b>전부가 정리 대상</b>이
     * 되어 살아 있는 팀원까지 지워진다. 이쪽은 같은 상황에서 정리 대상이 0건이 되므로
     * 안전 측으로 기운다.</p>
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 스윕이 한 주기에 N건을 훑어도 호출은 1회여야
     * 한다 — 건별로 {@link #findActiveMembership(Long)}을 부르면 그대로 N+1이 된다.</p>
     *
     * <p>존재하지 않는 식별자는 결과에 담기지 않는다. `team_members`·`occupancy_participants`의
     * FK가 {@code ON DELETE CASCADE}라 소속 행이 사라지면 참조 행도 함께 사라지므로,
     * "행은 없는데 참조만 남은" 상태는 발생하지 않는다.</p>
     *
     * @param membershipIds 검사할 소속 식별자. 비어 있으면 빈 결과
     * @return 그중 {@code ACTIVE}가 아닌 것. 정리 대상이다
     */
    public Set<Long> findInactiveMembershipIds(Collection<Long> membershipIds) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return Set.of();
        }
        return membershipRepository.findAllById(membershipIds).stream()
                .filter(membership -> membership.getStatus() != CohortMembershipStatus.ACTIVE)
                .map(CohortMembership::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
