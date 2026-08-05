package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;

import java.util.Optional;

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
}
