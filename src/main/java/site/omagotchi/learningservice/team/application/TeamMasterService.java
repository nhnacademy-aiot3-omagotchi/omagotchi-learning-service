package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 마스터 위임·팀 해체·자동 위임 (GR-12, GR-14, GR-16, GR-19, GR-20).
 *
 * <p>세 흐름을 한 클래스에 묶은 이유는 셋 다 <b>"팀당 MASTER 정확히 1명"</b>이라는 같은
 * 불변식을 공유하기 때문이다. {@code TeamMemberService}에 섞으면 위임 순서와 자동 위임
 * 기준이 팀원 추가 로직 사이에 흩어져, 어디서 MASTER가 0명이 되는지 추적하기 어려워진다.</p>
 *
 * <p><b>DB는 "최대 1명"만 보장한다.</b> {@code uq_team_members_one_master}가 부분 유니크라
 * 2명은 막지만 0명은 막지 못한다 — 최소 1명은 전적으로 트랜잭션 책임이며, 그래서 이
 * 클래스의 모든 메서드가 락으로 시작하고 커밋 전 카운트로 끝난다.</p>
 *
 * <p>락 순서는 {@code teams} → {@code team_members}(id 오름차순)로 고정한다. 팀원 추가·제외와
 * 같은 순서이며, 어기면 데드락이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMasterService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;

    /**
     * 마스터를 다른 팀원에게 넘긴다 (GR-14, GR-20).
     *
     * @param targetMemberId 넘겨받을 {@code team_members.id}. 계정 id가 아니다
     * @param userId         요청자. 이 팀의 MASTER여야 한다
     * @throws BusinessException 자기 자신에게 위임(400), MASTER 아님·팀 소속 아님(403),
     *                           팀 없음·대상 없음(404), 불변식 위반(409)
     */
    @Transactional
    public void delegate(Long teamId, Long targetMemberId, UUID userId) {

        Team team = accessSupport.lockActiveTeam(teamId);
        TeamMembership membership = accessSupport.requireActiveMembership(team.getCohortId(), userId);
        TeamMember currentMaster = accessSupport.requireMaster(teamId, membership.id());

        // 팀원 행 전체를 id 오름차순으로 잠근다. 정렬하지 않으면 두 위임 요청이 반대
        // 순서로 잠가 데드락이 난다. 대상 확인도 이 락 결과 안에서 해야 "위임 도중 대상이
        // 탈퇴"하는 경합이 직렬화된다 — 락 밖에서 찾으면 이미 사라진 행을 승격시킬 수 있다.
        List<TeamMember> lockedMembers = teamMemberRepository.lockAllByTeamId(teamId);

        TeamMember target = lockedMembers.stream()
                .filter(member -> member.getId().equals(targetMemberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(TeamErrorCode.MEMBER_NOT_FOUND));

        if (target.getId().equals(currentMaster.getId())) {
            throw new BusinessException(TeamErrorCode.CANNOT_DELEGATE_TO_SELF);
        }

        // 강등을 먼저 flush한 뒤에 승격한다. 역순이면 순간적으로 MASTER가 2명이 되어
        // uq_team_members_one_master를 위반한다. save가 saveAndFlush라 이 호출이 곧 flush다.
        currentMaster.demote();
        teamMemberRepository.save(currentMaster);

        target.promote();
        teamMemberRepository.save(target);

        requireExactlyOneMaster(teamId);
    }

    /**
     * 팀을 해체한다 (GR-19). MASTER만 수행할 수 있다.
     *
     * <p>팀원은 물리 삭제, 팀은 소프트 삭제다. 팀원을 남기면
     * {@code uq_team_members_membership}이 상태 조건 없는 유니크라 그 사람들이 어떤 팀에도
     * 다시 들어갈 수 없고, 팀 행을 지우면 이름 이력이 사라진다.</p>
     *
     * <p>해체 후 같은 이름으로 다시 만들 수 있다 —
     * {@code uq_teams_active_name}이 {@code WHERE deleted_at IS NULL}이기 때문이다.</p>
     *
     * @throws BusinessException MASTER 아님·팀 소속 아님(403), 팀 없음(404)
     */
    @Transactional
    public void disband(Long teamId, UUID userId) {

        Team team = accessSupport.lockActiveTeam(teamId);
        TeamMembership membership = accessSupport.requireActiveMembership(team.getCohortId(), userId);
        accessSupport.requireMaster(teamId, membership.id());

        teamMemberRepository.deleteByTeamId(teamId);
        team.disband();

        // TODO: 커밋 후 (구)팀원 전원에게 해체 통보를 발송한다 (GR-19, 알림 파트).
        //  점유의 RoomVacatedEvent와 같은 구조 — AFTER_COMMIT + @Async 리스너이며,
        //  발송 실패가 해체를 롤백시키면 안 된다.
        log.debug("팀이 해체됐습니다. teamId={}", teamId);
    }

    /**
     * 마스터가 팀을 떠날 때 뒤를 이을 팀원을 고른다 (GR-16).
     *
     * <p>기준은 {@code joined_at} 최소, 동률 시 {@code id} 최소다. <b>결정적이어야 한다</b> —
     * 회원 삭제 훅은 재시도될 수 있고, 두 번째 실행이 첫 번째와 다른 사람을 MASTER로
     * 만들면 멱등하지 않다.</p>
     *
     * <p>아직 호출부가 없다. 회원 삭제 훅(GR-16)의 진입점이 인증 파트와 미확정이라 그
     * 흐름을 만들 수 없지만, <b>선정 기준 자체는 여기서 확정하고 테스트로 고정한다</b> —
     * 기수 종료 연동(CE-01)도 이 로직을 재사용하도록 명세가 지정하고 있다.</p>
     *
     * @param leavingMemberId 떠나는 사람. 아직 행이 남아 있을 수 있어 후보에서 제외한다
     * @return 후보가 없으면 {@code Optional.empty()} — 팀을 소프트 삭제해야 한다는 뜻이다
     */
    public Optional<TeamMember> findSuccessor(Long teamId, Long leavingMemberId) {
        return teamMemberRepository.findSuccessorCandidates(teamId, leavingMemberId).stream()
                .findFirst();
    }

    /**
     * 커밋 전 불변식 검증 (GR-12).
     *
     * <p>부분 유니크가 잡지 못하는 "0명"을 여기서 잡는다. 위임 로직이 정상이면 도달할 수
     * 없지만, 남겨두는 이유는 실패 비용이 비대칭이기 때문이다 — MASTER 없는 팀이 커밋되면
     * 아무도 그 팀을 해체하거나 팀원을 관리할 수 없고, 되살릴 API도 없다.</p>
     */
    private void requireExactlyOneMaster(Long teamId) {
        long masterCount = teamMemberRepository.countByTeamIdAndRole(teamId, TeamMemberRole.MASTER);
        if (masterCount != 1) {
            throw new BusinessException(TeamErrorCode.MASTER_STATE_CONFLICT);
        }
    }
}
