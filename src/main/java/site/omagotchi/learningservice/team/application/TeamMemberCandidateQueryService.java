package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateStatus;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 팀 마스터가 같은 기수의 팀원 후보를 이름 또는 이메일로 검색하는 조회 서비스.
 *
 * <p>Identity 검색을 기다리는 동안 Learning DB 트랜잭션과 커넥션을 붙들지 않는다.
 * 이 목록은 조회 시점의 안내용 스냅샷이며, 실제 추가 가능 여부는 팀원 추가 명령이
 * 팀 행을 잠근 뒤 다시 검증한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TeamMemberCandidateQueryService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_RESULTS = 20;

    private final TeamAccessSupport accessSupport;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final TeamMemberRepository teamMemberRepository;
    private final IdentityAccountClient identityAccountClient;

    public List<TeamMemberCandidateResult> search(
            Long teamId,
            String query,
            UUID requesterUserId
    ) {
        Team team = accessSupport.loadActiveTeam(teamId);
        TeamMembership requesterMembership = accessSupport.requireActiveMembership(
                team.getCohortId(), requesterUserId);
        accessSupport.requireMaster(teamId, requesterMembership.id());
        String normalizedQuery = normalizeQuery(query);

        Map<UUID, CohortMembershipView> membershipsByUser =
                cohortMembershipQueryService.findActiveMemberships(team.getCohortId()).stream()
                        .collect(Collectors.toMap(
                                CohortMembershipView::userId,
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new
                        ));
        if (membershipsByUser.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> teamIdsByMembership = teamMemberRepository
                .findByCohortMembershipIdIn(membershipsByUser.values().stream()
                        .map(CohortMembershipView::membershipId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        TeamMember::getCohortMembershipId,
                        TeamMember::getTeamId
                ));

        return identityAccountClient.search(normalizedQuery, membershipsByUser.keySet()).stream()
                .filter(account -> account.status() == IdentityAccountState.ACTIVE)
                .limit(MAX_RESULTS)
                .map(account -> new TeamMemberCandidateResult(
                        account.accountId(),
                        account.displayName(),
                        account.email(),
                        status(teamId, teamIdsByMembership.get(
                                membershipsByUser.get(account.accountId()).membershipId()))
                ))
                .toList();
    }

    private static String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank() || normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(TeamErrorCode.INVALID_MEMBER_QUERY);
        }
        return normalized;
    }

    private static TeamMemberCandidateStatus status(Long teamId, Long assignedTeamId) {
        if (assignedTeamId == null) {
            return TeamMemberCandidateStatus.AVAILABLE;
        }
        return teamId.equals(assignedTeamId)
                ? TeamMemberCandidateStatus.ALREADY_IN_THIS_TEAM
                : TeamMemberCandidateStatus.IN_ANOTHER_TEAM;
    }
}
