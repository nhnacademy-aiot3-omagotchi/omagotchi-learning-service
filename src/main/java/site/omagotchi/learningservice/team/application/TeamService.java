package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.reuslt.TeamDetailResponse;
import site.omagotchi.learningservice.team.application.reuslt.TeamMemberResponse;
import site.omagotchi.learningservice.team.application.reuslt.TeamResponse;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.team.application.port.AccountReader;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 팀 생성과 조회 (GR-01, GR-02, GR-06, GR-15).
 *
 * <p>팀원 추가·제외·탈퇴는 {@link TeamMemberService}, 위임·해체는 별도 서비스가 맡는다.
 * 이 클래스는 "팀이라는 단위를 만들고 보여주는" 책임만 갖는다.</p>
 *
 * <p>생성에서 {@code teams} INSERT와 {@code team_members} INSERT는 반드시 한 트랜잭션이다 —
 * 중간에 끊기면 MASTER 없는 팀이 남고, 그 팀은 아무도 해체할 수 없다.</p>
 *
 * <p>조회 응답에는 내부 식별자(user_id, cohort_membership_id)를 넣지 않는다 (GR-15).
 * 표시명은 Identity Service의 {@code accounts.name}이 출처이며,
 * membership → user_id → name 두 단계를 모두 배치로 조회한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final AccountReader accountReader;

    /**
     * 팀을 생성하고 생성자를 MASTER로 등록한다 (GR-01, GR-02, GR-18, GR-21).
     *
     * <p>중복 이름·기소속 검사를 먼저 하지만 그것으로 끝이 아니다. 두 요청이 같은 시점에
     * "없음"을 확인하고 둘 다 INSERT할 수 있어서, 선검사는 정상 경로에 친절한 메시지를 주는
     * 용도이고 실제 방어선은 {@code uq_teams_active_name}과 {@code uq_team_members_membership}이다.
     * 인덱스 위반을 같은 에러 코드로 되돌리는 일은 Port 구현이 맡으므로 여기서는
     * 두 경로가 같은 예외로 보인다.</p>
     *
     * <p>{@code request.cohortId()}는 null일 수 있다 (RM-28). 활성 기수가 하나면 서버가
     * 결정하고, 둘 이상이면 지정을 요구한다 — 자세한 분기는
     * {@link TeamAccessSupport#resolveMembershipForCreate(Long, UUID)} 참고.</p>
     *
     * @param userId JWT에서 꺼낸 요청자 계정 id. 요청 본문의 cohortId가 이 계정의 것인지 서버가 검증한다
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         이름 규칙 위반(400), 담당하지 않는 기수(403), 이름 중복·이미 팀 소속(409)
     */
    @Transactional
    public TeamResponse create(CreateTeamRequest request, UUID userId) {
        TeamMembership membership = accessSupport.resolveMembershipForCreate(request.cohortId(), userId);

        // 도메인은 규칙을 boolean으로만 표현한다. 그것을 사용자 대상 400으로 옮기는 것이 여기 책임이다 —
        // Team이 직접 BusinessException을 던지면 도메인이 외부 오류 계약을 알게 된다.
        String name = Team.normalizeName(request.name());
        if (!Team.isValidName(name)) {
            throw new BusinessException(TeamErrorCode.INVALID_NAME);
        }

        if (teamRepository.existsActiveByCohortIdAndName(membership.cohortId(), name)) {
            throw new BusinessException(TeamErrorCode.DUPLICATE_NAME);
        }

        // GR-18은 기수 내 제약이다. 다른 기수의 팀에 소속된 것은 무관하며,
        // membership.id() 기준 검사가 곧 "기수당 1인 1팀"이 된다.
        if (teamMemberRepository.existsByCohortMembershipId(membership.id())) {
            throw new BusinessException(TeamErrorCode.ALREADY_IN_TEAM);
        }

        // 인덱스 위반의 ErrorCode 변환은 Port 구현이 책임진다 —
        // 여기서 기술 예외를 잡으면 Application이 Spring Data와 인덱스명을 알게 된다.
        Team team = teamRepository.save(Team.create(membership.cohortId(), name));
        teamMemberRepository.save(TeamMember.master(team.getId(), membership.id()));
        return TeamResponse.from(team);
    }

    /**
     * 사용자가 속한 팀 목록 (GR-06).
     * 여러 기수를 담당하는 매니저·멘토는 기수별로 하나씩, 복수 건이 정상이다.
     */
    public List<TeamResponse> getMyTeams(UUID userId) {
        List<Long> membershipIds = cohortMembershipQueryService.findActiveMemberships(userId).stream()
                .map(CohortMembershipView::membershipId)
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
        TeamMembership membership = accessSupport.requireActiveMembership(team.getCohortId(), userId);
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

        Map<Long, UUID> userIdsByMembership = cohortMembershipQueryService.findUserIds(membershipIds);
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
