package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.application.result.CurrentTeamMembershipView;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 다른 Module에 현재 팀 소속을 제공하는 공개 Application Interface.
 *
 * <p>membership과 team을 배치로 읽어 해체된 팀과 다른 기수의 팀을 제외한
 * 평면 매핑만 제공한다. 특정 팀 필터와 그룹화는 호출하는 Ranking Module이 소유한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentTeamMembershipQueryService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    // 유효한 기수 membership 전체를 활성 상태의 현재 팀 소속과 일괄 연결한다.
    public List<CurrentTeamMembershipView> findCurrentMemberships(
            Long cohortId,
            Collection<Long> eligibleMembershipIds
    ) {
        List<Long> membershipIds = distinctIds(eligibleMembershipIds);
        if (cohortId == null || membershipIds.isEmpty()) {
            return List.of();
        }

        List<TeamMember> members = teamMemberRepository
                .findByCohortMembershipIdIn(membershipIds);
        if (members.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = members.stream()
                .map(TeamMember::getTeamId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> activeTeams = teamRepository
                .findByIdInAndDeletedAtIsNull(teamIds)
                .stream()
                .filter(team -> Objects.equals(team.getCohortId(), cohortId))
                .collect(Collectors.toMap(
                        Team::getId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return toViews(members, activeTeams);
    }

    // 팀원과 활성 팀을 결합해 다른 기수 또는 해체된 팀을 제외한 평면 뷰로 변환한다.
    private List<CurrentTeamMembershipView> toViews(
            List<TeamMember> members,
            Map<Long, Team> activeTeams
    ) {
        return members.stream()
                .flatMap(member -> Optional.ofNullable(activeTeams.get(member.getTeamId()))
                        .map(team -> new CurrentTeamMembershipView(
                                team.getId(),
                                team.getName(),
                                member.getCohortMembershipId()
                        ))
                        .stream())
                .sorted(viewOrder())
                .toList();
    }

    // 배치 조회 전에 null을 제거하고 입력 순서를 유지한 중복 없는 식별자 목록을 만든다.
    private List<Long> distinctIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    // 팀과 membership 식별자 순으로 결과를 고정해 호출마다 동일한 순서를 보장한다.
    private Comparator<CurrentTeamMembershipView> viewOrder() {
        return Comparator.comparing(CurrentTeamMembershipView::teamId)
                .thenComparing(CurrentTeamMembershipView::cohortMembershipId);
    }
}
