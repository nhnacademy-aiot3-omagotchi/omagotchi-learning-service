package site.omagotchi.learningservice.cohort.application.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 기수 소속 하나가 끝났다.
 *
 * <p><b>여러 외부 사건이 여기로 수렴한다.</b> 계정 삭제(GR-16, MR-26)와 수동 제명은 모두
 * "이 멤버십은 더 이상 유효하지 않다"는 같은 사실이 되고, 팀·점유는 그 사실 하나만 알면
 * 된다 — 두 파트가 소속을 {@code cohort_membership_id}로 키잡고 있기 때문이다.
 * 진입점을 통일하지 않으면 같은 정리 로직이 사건 종류만큼 복제된다
 * (master-checklist "회원 삭제 훅 트리거 계약을 점유 훅과 통일").</p>
 *
 * <p><b>기수 종료(CE)는 이 이벤트로 대체하지 않는다.</b> CE-05가 팀 정리 → 대기 알림 삭제
 * → 점유 종료 → 실습실 해제의 순서를 강제하는데, 멤버십 단위로 팬아웃하면 비동기라
 * 순서를 보장할 수 없다. 게다가 CE-04({@code spaces.cohort_id} 해제)는 기수 단위라
 * 멤버십 이벤트로 표현되지 않고, CE-01은 팀을 통째로 해체해 자동 위임을 하지 않는다는
 * 점에서 계정 삭제와 의미가 다르다.</p>
 *
 * <p>수신 측이 지켜야 할 것은 {@code OccupancyEventPublisher}의 javadoc과 같다
 * (ADR space-team/0006) — {@code AFTER_COMMIT} + {@code @Async} +
 * {@code REQUIRES_NEW}. 특히 마지막을 빠뜨리면 정리 결과가 조용히 유실된다.</p>
 *
 * @param membershipId 끝난 소속. 팀·점유의 정리는 전부 이 값을 키로 한다
 * @param cohortId     그 소속의 기수
 * @param userId       계정. 점유의 배타 제약이 계정 기준이라(MR-10) 함께 필요하다
 * @param endedAt      종료 시각. 수신이 늦어도 "언제 끝났는지"는 이 값이 정본이다
 */
public record CohortMembershipEndedEvent(
        Long membershipId,
        Long cohortId,
        UUID userId,
        OffsetDateTime endedAt
) {
}
