package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.result.TeamDetailLocalResult;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 팀 상세에 필요한 Learning DB 값을 하나의 읽기 트랜잭션에서 복사한다.
 *
 * <p>Identity 표시 이름은 이 Component의 책임이 아니다. 여기서 엔티티와 멤버십 참조를
 * {@link TeamDetailLocalResult}로 복사해 반환하면 트랜잭션이 끝나고, 호출자가 그 뒤에
 * Identity를 조회한다. 외부 응답을 기다리는 동안 EntityManager나 JDBC Connection을
 * 붙들지 않는 것이 이 Component를 분리한 이유다.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamDetailLookup {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final CohortMembershipQueryService cohortMembershipQueryService;

    /**
     * 팀 접근 권한을 확인하고 팀·팀원·계정 논리 참조를 불변 값으로 반환한다.
     */
    public TeamDetailLocalResult load(Long teamId, UUID userId) {
        Team team = accessSupport.loadActiveTeam(teamId);
        TeamMembership membership = accessSupport.requireActiveMembership(team.getCohortId(), userId);
        TeamMember requesterMember = accessSupport.requireMembership(teamId, membership.id());

        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId);
        Map<Long, UUID> userIdsByMembership = cohortMembershipQueryService.findUserIds(
                members.stream()
                        .map(TeamMember::getCohortMembershipId)
                        .toList()
        );

        List<TeamDetailLocalResult.Member> localMembers = members.stream()
                .sorted(Comparator
                        .comparing(TeamMember::isMaster).reversed()
                        .thenComparing(TeamMember::getJoinedAt)
                        .thenComparing(TeamMember::getId))
                .map(member -> TeamDetailLocalResult.Member.from(
                        member,
                        userIdsByMembership.get(member.getCohortMembershipId())
                ))
                .toList();

        return TeamDetailLocalResult.of(team, requesterMember, localMembers);
    }
}
