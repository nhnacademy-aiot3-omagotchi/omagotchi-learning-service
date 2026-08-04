package site.omagotchi.learningservice.occupancy.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 재실 조회 파사드. 점유 모듈이 출결 파트에 의존하는 유일한 통로다.
 *
 * <p>점유 시작이 기수 컨텍스트를 요청받지 않는 이유가 여기 있다 (명세서 02) —
 * 점유는 재실이 전제 조건이므로(MR-22), 열린 재실 구간의 {@code cohort_membership_id}를
 * 그대로 점유자 멤버십으로 쓴다. 출근한 기수로 점유하는 것이 자연스럽고,
 * 다기수 담당자도 별도 선택 없이 결정된다.</p>
 *
 * <p>재실 공간 id를 반환하지 않는 것은 의도다. 점유하려는 방과 현재 재실 공간이
 * 같아야 한다는 규칙은 명세에 없다 — 참여자의 위치가 다른 공간이어도 정상으로
 * 취급한다는 규정(명세서 02)과 같은 맥락이다. 쓰지 않는 값을 계약에 두지 않는다.</p>
 *
 * <p>실패를 {@code Optional.empty()}(재실 아님, 403)와 예외(조회 불가)로
 * 구분한다. 재실 여부를 확인할 수 없을 때 통과시키면 안 되므로 구현은 반드시
 * 실패를 삼키지 말고 던져야 한다. 명세서(02)는 조회 불가를 503으로 요구하지만,
 * 공용 {@code ErrorType}에 아직 대응 값이 없어 호출부는 당분간 400으로 옮긴다
 * ({@code OccupancyErrorCode#PRESENCE_UNAVAILABLE} 참고).</p>
 */
public interface PresenceReader {

    /**
     * 열린 재실 구간을 조회한다.
     *
     * @return 재실 중이 아니면 {@code Optional.empty()}
     * @throws RuntimeException 조회 자체가 실패한 경우. 호출부가 오류로 옮긴다
     */
    Optional<PresenceContext> findOpenPresence(UUID userId);

    /** 열린 재실 구간에서 도출한 점유자 멤버십. */
    record PresenceContext(Long cohortMembershipId) {
    }
}
