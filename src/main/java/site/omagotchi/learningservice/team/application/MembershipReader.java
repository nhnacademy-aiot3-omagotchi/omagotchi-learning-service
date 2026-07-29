package site.omagotchi.learningservice.team.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 기수 멤버십 조회 파사드. 팀 모듈이 cohort 모듈에 의존하는 유일한 통로다.
 *
 * 읽기 전용이다. 팀이 멤버십을 변경할 일은 없고, 반대 방향(기수 종료 → 팀 정리,
 * 회원 삭제 → 팀 정리)은 파사드가 아니라 이벤트/훅으로 들어온다.
 *
 * Optional/Map을 반환하고 예외를 던지지 않는 것이 의도다. 같은 "멤버십 없음"이
 * 팀 생성에서는 403, 팀원 추가에서는 400이라 상황별 에러 코드는 호출부가 정해야 한다.
 */
public interface MembershipReader {

    /**
     * 특정 기수의 ACTIVE 멤버십을 조회한다.
     * 팀 생성 시 요청자 검증(RM-28), 팀원 추가 시 대상 기수 정합 검증(GR-22)에 쓰인다.
     */
    Optional<TeamMembership> findActive(Long cohortId, UUID userId);

    /**
     * 사용자의 ACTIVE 멤버십 전체를 조회한다.
     * 팀 생성 시 cohortId가 생략된 경우의 분기(0개 403 / 2개 이상 400)에 쓰인다.
     */
    List<TeamMembership> findActiveAll(UUID userId);

    /**
     * membershipId → userId 매핑. 팀원 목록 표시명 조회 경로의 첫 단계(GR-15).
     * 반드시 배치로 호출한다 — 기수 모듈이 분리되면 이 시그니처가 배치 API 한 번이 된다.
     */
    Map<Long, UUID> findUserIds(Collection<Long> membershipIds);
}