package site.omagotchi.learningservice.team.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.team.application.port.MembershipReader;
import site.omagotchi.learningservice.team.application.TeamMembership;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MembershipReader의 구현.
 *
 * TODO: 현재 cohort.infrastructure/domain 을 직접 참조하고 있어 계층 위반이다.
 *       기수 파트에 CohortMembershipQuery(application 계층 읽기 계약)를 요청해둔 상태이며,
 *       도착하면 이 파일의 의존 대상만 교체한다. 인터페이스와 팀 서비스는 그대로다.
 *       ArchUnit 규칙(팀 → cohort 내부 금지)은 그때까지 @Disabled 다.
 */
@Component
@RequiredArgsConstructor
public class CohortMembershipQueryReader implements MembershipReader {

    private final CohortMembershipRepository membershipRepository;

    @Override
    public Optional<TeamMembership> findActive(Long cohortId, UUID userId) {
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId,
                        userId,
                        CohortMembershipStatus.ACTIVE
                )
                .map(this::toTeamMembership);
    }

    @Override
    public List<TeamMembership> findActiveAll(UUID userId) {
        // TODO: CohortMembershipRepository.findByUserIdAndStatus 추가 후 교체.
        //  한 계정의 멤버십은 많아야 기수 수만큼이라 인메모리 필터로도 충분하다.
        return membershipRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .filter(membership -> membership.getStatus() == CohortMembershipStatus.ACTIVE)
                .map(this::toTeamMembership)
                .toList();
    }

    @Override
    public Map<Long, UUID> findUserIds(Collection<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        return membershipRepository.findAllById(membershipIds).stream()
                .collect(Collectors.toMap(
                        CohortMembership::getId,
                        CohortMembership::getUserId
                ));
    }

    private TeamMembership toTeamMembership(CohortMembership membership) {
        return new TeamMembership(
                membership.getId(),
                membership.getCohortId(),
                membership.getUserId()
        );
    }
}