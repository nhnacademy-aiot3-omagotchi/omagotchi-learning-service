package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.infrastructure.TeamMemberRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final MembershipReader  membershipReader;
    private final AccountReader accountReader;


    /**
     * 팀원 추가 (GR-03). 수락 절차 없이 즉시 반영된다.
     * <p>
     * 검증을 락 밖에서 먼저 끝내고 락 구간에는 카운트와 INSERT만 남긴다 —
     * 계정 조회는 외부 호출이라 락 안에 넣으면 그 시간만큼 다른 요청이 전부 대기한다.
     */
    @Transactional
    public void addMember(Long teamId, AddTeamMemberRequest request, UUID userId) {
        Team team = accessSupport.loadActiveTeam(teamId);
        TeamMembership requestMembership =
                accessSupport.requiredActiveMembership(team.getCohortId(), userId);
        accessSupport.requireMaster(teamId, requestMembership.id());

        validateAccount(request.targetUserId());


        // GR-22: 팀의 기수로 대상 멤버십을 역조회한다.
        // 조회 방향을 뒤집으면 "대상의 기수 == 팀의 기수" 검증이 조회 결과로 자동 충족된다.
        // team_members에 cohort_id가 없으므로 이 앱 검증이 유일한 방어선이다.
        TeamMembership targetMembership = membershipReader
                .findActive(team.getCohortId(), request.targetUserId())
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TARGET_NOT_IN_COHORT));


        // 여기부터 락 구간.
        // 정원은 "최대 8행"이라 유니크 인덱스로 표현할 수 없다 — 락이 유일한 방어선이고,
        // 락 밖에서 세면 7명 팀에 둘이 동시에 들어와 9명이 된다.
        Team lockedTeam = accessSupport.lockActiveTeam(teamId);
        if (teamMemberRepository.countByTeamId(lockedTeam.getId()) >= TeamMember.MAX_MEMBERS_PER_TEAM) {
            throw new BusinessException(TeamErrorCode.CAPACITY_EXCEEDED);
        }

        try {
            teamMemberRepository.saveAndFlush(
                    TeamMember.member(lockedTeam.getId(), targetMembership.id())
            );
        } catch (DataIntegrityViolationException exception) {
            // 대상이 이미 같은 기수의 다른 팀 소속 → uq_team_members_membership 위반 (GR-10, GR-18)
            throw TeamConstraintTranslator.translate(exception);
        }
    }

    @Transactional
    public void kickMember(Long teamId, Long targetMemberId, UUID userId) {
        Team team = accessSupport.lockActiveTeam(teamId);
        TeamMembership requestMembership =
                accessSupport.requiredActiveMembership(team.getCohortId(), userId);
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
                accessSupport.requiredActiveMembership(team.getCohortId(), userId);
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
