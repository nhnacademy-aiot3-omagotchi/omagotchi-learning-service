package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.dto.result.TeamDetailResponse;
import site.omagotchi.learningservice.team.application.dto.result.TeamMemberResponse;
import site.omagotchi.learningservice.team.application.dto.result.TeamResponse;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.infrastructure.TeamMemberRepository;
import site.omagotchi.learningservice.team.infrastructure.TeamRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 팀을 생성하고 생성자를 MASTER로 등록한다 (GR-01, GR-02).
 *
 * teams INSERT와 team_members INSERT는 반드시 한 트랜잭션이다 —
 * 중간에 끊기면 MASTER 없는 팀이 남고, 그 팀은 아무도 해체할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final MembershipReader membershipReader;
    private final AccountReader accountReader;

    @Transactional
    public TeamResponse create(CreateTeamRequest request, UUID userId) {
        TeamMembership membership = accessSupport.resolveMembershipForCreate(request.cohortId(), userId);
        String name = Team.normalizeName(request.name());

        if (teamRepository.existsActiveByCohortIdAndName(membership.cohortId(), name)) {
            throw new BusinessException(TeamErrorCode.DUPLICATE_NAME);
        }

        // GR-18은 기수 내 제약이다. 다른 기수의 팀에 소속된 것은 무관하며,
        // membership.id() 기준 검사가 곧 "기수당 1인 1팀"이 된다.
        if (teamMemberRepository.existsByCohortMembershipId(membership.id())) {
            throw new BusinessException(TeamErrorCode.ALREADY_IN_TEAM);
        }

        try {
            // saveAndFlush를 쓴 이유는 save만 하면 flush가 커밋 시점으로 밀려서 try 밖에서 예외가 터지기 때문이다.
            // 그러면 변환기가 안 잡습니다.
            Team team = teamRepository.saveAndFlush(Team.create(membership.cohortId(), name));
            teamMemberRepository.saveAndFlush(TeamMember.master(team.getId(), membership.id()));
            return TeamResponse.from(team);
        } catch (DataIntegrityViolationException e) {
            throw TeamConstraintTranslator.translate(e);
        }
    }

    /**
     * 사용자가 속한 팀 목록 (GR-06).
     * 여러 기수를 담당하는 매니저·멘토는 기수별로 하나씩, 복수 건이 정상이다.
     */
    public List<TeamResponse> getMyTeams(UUID userId) {
        List<Long> membershipIds = membershipReader.findActiveAll(userId).stream()
                .map(TeamMembership::id)
                .toList();
        if (membershipIds.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = teamMemberRepository.findByCohortMembershipIdIn(membershipIds).stream()
                .map(TeamMember::getTeamId)
                .toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }

        return teamRepository.findByIdInAndDeletedAtIsNull(teamIds).stream()
                .map(TeamResponse::from)
                .toList();
    }


    /**
     * 팀 상세와 팀원 목록 (GR-15). 소속자만 조회할 수 있다.
     */
    public TeamDetailResponse getTeam(Long teamId, UUID userId) {
        Team team = accessSupport.loadActiveTeam(teamId);
        TeamMembership membership = accessSupport.requiredActiveMembership(team.getCohortId(), userId);
        accessSupport.requireMembership(teamId, membership.id());

        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId);
        return TeamDetailResponse.of(team, toMemberResponse(members));
    }

    /**
     * membership → user_id → accounts.name 경로.
     * 두 단계 모두 배치라 팀원이 몇 명이든 외부 조회는 2회로 고정된다.
     */
    private List<TeamMemberResponse> toMemberResponse(List<TeamMember> members) {
        List<Long> membershipIds = members.stream()
                .map(TeamMember::getCohortMembershipId)
                .toList();

        Map<Long, UUID> userIdsByMembership = membershipReader.findUserIds(membershipIds);
        Map<UUID, String> displayNames = accountReader.findDisplayNames(userIdsByMembership.values());

        return members.stream()
                .sorted(Comparator
                        .comparing(TeamMember::isMaster).reversed()
                        .thenComparing(TeamMember::getJoinedAt)
                        .thenComparing(TeamMember::getId))
                .map(member -> {
                    UUID memberUserId = userIdsByMembership.get(member.getCohortMembershipId());
                    String displayName = memberUserId == null ? null : displayNames.get(memberUserId);
                    return TeamMemberResponse.of(member, displayName);
                })
                .toList();
    }
}
