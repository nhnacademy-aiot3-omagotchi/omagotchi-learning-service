package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.AccountReader;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;

import java.util.UUID;

/**
 * 팀원 추가·제외·탈퇴 (GR-03, GR-05, GR-07, GR-08, GR-13).
 *
 * <p>세 연산 모두 {@code teams} 행 락을 먼저 잡는다. 락 순서를 teams → team_members로
 * 고정하는 것이 데드락 방지 규칙이며, 위임(#8)도 같은 순서를 따라야 한다.</p>
 *
 * <p>이 도메인에서 DB가 막아주지 못하는 제약이 둘 있다. 정원 8명(GR-17)은 "최대 N행"이라
 * 유니크로 표현할 수 없어 락 안 카운트가 유일한 방어선이고, 팀원의 기수 정합(GR-22)은
 * ERD v3에서 {@code team_members.cohort_id}와 복합 FK가 사라져 애플리케이션 검증이
 * 유일한 방어선이다. 둘 다 테스트로 고정되어 있어야 한다.</p>
 *
 * <p>탈퇴·제외는 전부 물리 삭제다. 소프트 삭제하면 옛 행이
 * {@code uq_team_members_membership}을 계속 점유해 재가입이 영구히 불가능해진다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final AccountReader accountReader;


    /**
     * 팀원 추가 (GR-03). 수락 절차 없이 즉시 반영된다.
     * <p>
     * 검증을 락 밖에서 먼저 끝내고 락 구간에는 카운트와 INSERT만 남긴다 —
     * 계정 조회는 외부 호출이라 락 안에 넣으면 그 시간만큼 다른 요청이 전부 대기한다.
     * <p>
     * 락 전 단계에서 Team을 엔티티로 읽지 않는 것이 핵심이다. 읽어두면 1차 캐시의
     * 인스턴스가 lockActiveTeam의 반환값이 되어 해체 재확인이 락 이전 상태를 본다.
     */
    @Transactional
    public void addMember(Long teamId, AddTeamMemberRequest request, UUID userId) {
        Long cohortId = accessSupport.requireActiveTeamCohortId(teamId);
        TeamMembership requestMembership =
                accessSupport.requireActiveMembership(cohortId, userId);
        accessSupport.requireMaster(teamId, requestMembership.id());

        validateAccount(request.targetUserId());


        // GR-22: 팀의 기수로 대상 멤버십을 역조회한다.
        // 조회 방향을 뒤집으면 "대상의 기수 == 팀의 기수" 검증이 조회 결과로 자동 충족된다.
        // team_members에 cohort_id가 없으므로 이 앱 검증이 유일한 방어선이다.
        TeamMembership targetMembership = cohortMembershipQueryService
                .findActiveMembership(cohortId, request.targetUserId())
                .map(view -> new TeamMembership(view.membershipId(), view.cohortId(), view.userId()))
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TARGET_NOT_IN_COHORT));


        // 여기부터 락 구간.
        // 정원은 "최대 8행"이라 유니크 인덱스로 표현할 수 없다 — 락이 유일한 방어선이고,
        // 락 밖에서 세면 7명 팀에 둘이 동시에 들어와 9명이 된다.
        Team lockedTeam = accessSupport.lockActiveTeam(teamId);
        if (teamMemberRepository.countByTeamId(lockedTeam.getId()) >= TeamMember.MAX_MEMBERS_PER_TEAM) {
            throw new BusinessException(TeamErrorCode.CAPACITY_EXCEEDED);
        }

        // 대상이 이미 같은 기수의 다른 팀 소속이면 uq_team_members_membership 위반이 되고 (GR-10, GR-18),
        // Port 구현이 그것을 ALREADY_IN_TEAM으로 변환해 던진다.
        teamMemberRepository.save(TeamMember.member(lockedTeam.getId(), targetMembership.id()));
    }

    /**
     * 팀원 제외 (GR-05). MASTER만 수행할 수 있고, 대상 행은 물리 삭제된다.
     *
     * <p>대상을 {@code team_members.id}로 지정하는 것이 의도다. 계정 id나 멤버십 id를 받으면
     * 팀 모듈이 소유하지 않은 식별자를 API 표면에 노출하게 되고(GR-15 위반),
     * 조회 응답의 {@code memberId}를 그대로 되돌려주는 흐름도 깨진다.
     * 대신 다른 팀의 memberId가 올 수 있으므로 팀 소속 여부를 반드시 재확인한다.</p>
     *
     * <p>MASTER 본인은 제외 대상이 될 수 없다 — 팀에 MASTER가 0명인 상태가 만들어지기 때문이다.
     * 마스터가 팀을 떠나려면 {@link #leave(Long, UUID)}(단독일 때) 또는 위임(#8)을 거쳐야 한다.</p>
     *
     * @param targetMemberId 제외할 {@code team_members.id}. 계정 id가 아니다
     * @param userId         요청자 계정 id. 이 팀의 MASTER여야 한다
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         마스터 자기 제외(400), 요청자가 MASTER 아님(403), 팀·팀원 없음(404)
     */
    @Transactional
    public void kickMember(Long teamId, Long targetMemberId, UUID userId) {
        Team team = accessSupport.lockActiveTeam(teamId);
        TeamMembership requestMembership =
                accessSupport.requireActiveMembership(team.getCohortId(), userId);
        accessSupport.requireMaster(teamId, requestMembership.id());

        TeamMember target = teamMemberRepository.findById(targetMemberId)
                .filter(member -> member.getTeamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(TeamErrorCode.MEMBER_NOT_FOUND));

        if (target.isMaster()) {
            throw new BusinessException(TeamErrorCode.MASTER_CANNOT_BE_KICKED);
        }

        // 물리 삭제다. 소프트 삭제하면 옛 행이 uq_team_members_membership을
        // 계속 점유해 그 사람리 어떤 팀에도 다시 못 들어간다.
        teamMemberRepository.delete(target);
    }


    /**
     * 탈퇴 (GR-07, GR-08, GR-13).
     * 일반 팀원은 즉시 나간다. 마스터는 팀원이 남아 있으면 409로 거부하고,
     * 유일 팀원이면 행 삭제와 팀 소프트 삭제를 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public void leave(Long teamId, UUID userId) {
        Team team = accessSupport.lockActiveTeam(teamId);
        TeamMembership membership =
                accessSupport.requireActiveMembership(team.getCohortId(), userId);
        TeamMember member = accessSupport.requireMembership(teamId, membership.id());

        if (member.isMaster()) {
            // 마스터 없는 팀이 남으면 아무도 그 팀을 해체할 수 없다.
            long memberCount = teamMemberRepository.countByTeamId(teamId);
            if (memberCount > 1) {
                throw new BusinessException(TeamErrorCode.DELEGATION_REQUIRED);
            }
            teamMemberRepository.delete(member);
            team.disband();
            return;
        }

        teamMemberRepository.delete(member);
    }


//---------------------------------내부 헬퍼---------------------------------------------------

    /**
     * 대상 계정의 존재·미탈퇴를 확인한다 (GR-11).
     *
     * <p>미존재(404)와 탈퇴(409)를 나누는 이유는 프런트가 다르게 안내해야 하기 때문이다 —
     * 전자는 오타나 잘못된 대상, 후자는 실재했으나 지금은 초대할 수 없는 사람이다.
     * 그래서 {@link AccountReader}가 boolean이 아니라 3-state를 돌려준다.</p>
     *
     * <p>계정은 Identity Service 소유라 서비스 간 DB 외래 키가 없다. 즉 이 확인을
     * 건너뛰면 탈퇴 계정도 그대로 팀에 들어간다 — DB가 대신 막아주지 않는다.</p>
     */
    private void validateAccount(UUID targetUserId) {
        AccountReader.AccountState state = accountReader.findState(targetUserId);
        if (state == AccountReader.AccountState.NOT_FOUND) {
            throw new BusinessException(TeamErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (state == AccountReader.AccountState.WITHDRAWN) {
            throw new BusinessException(TeamErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
