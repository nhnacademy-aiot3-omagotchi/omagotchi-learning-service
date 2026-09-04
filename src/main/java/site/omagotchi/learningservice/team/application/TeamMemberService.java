package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountSnapshot;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.UUID;

/**
 * 팀원 추가·제외·탈퇴 (GR-03, GR-05, GR-07, GR-08, GR-13).
 *
 * <p>상태 변경 트랜잭션은 모두 {@code teams} 행을 먼저 잠근다. 팀원 추가는 쓰기 트랜잭션을
 * 시작하기 전에 Identity 조회 전 접근 제어와 계정 조회를 끝내고, 실제 저장 트랜잭션은 팀 락으로
 * 시작한다. 락 순서를 teams → team_members로 고정하는 것이 데드락 방지 규칙이며,
 * 위임(#8)도 같은 순서를 따른다.</p>
 *
 * <p>이 도메인에서 DB가 막아주지 못하는 제약이 둘 있다. 정원 8명(GR-17)은 "최대 N행"이라
 * 유니크로 표현할 수 없어 락 안 카운트가 유일한 방어선이고, 팀원의 기수 정합(GR-22)은
 * ERD v3에서 {@code team_members.cohort_id}와 복합 FK가 사라져 애플리케이션 검증이
 * 유일한 방어선이다. 둘 다 테스트로 고정되어 있어야 한다.</p>
 *
 * <p>탈퇴·제외는 전부 물리 삭제다. 소프트 삭제하면 옛 행이
 * {@code uq_team_members_membership}을 계속 점유해 재가입이 영구히 불가능해진다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final IdentityAccountClient identityAccountClient;
    private final TeamMemberAddition teamMemberAddition;

    /**
     * 팀원 추가 (GR-03). 수락 절차 없이 즉시 반영된다.
     * <p>
     * Identity 조회 전 접근 제어를 먼저 끝내고 계정 조회는 쓰기 트랜잭션 밖에서 수행한다.
     * 외부 호출 뒤의 팀 상태·권한·정원 판단은 {@link TeamMemberAddition}이
     * 팀 행 락을 잡은 별도 트랜잭션에서 다시 수행한다.
     * <p>
     * 이 접근 제어는 빠른 실패와 권한 없는 요청의 Identity 계정 탐색을 막기
     * 위한 것일 뿐 정합성 방어선이 아니다. Identity 조회 중 팀이 해체되거나 MASTER가
     * 위임될 수 있으므로 저장 여부는 쓰기 트랜잭션의 락 이후 재검증만으로 결정한다.
     *
     * @param targetUserId 추가할 계정 id. 어느 기수의 멤버십으로 넣을지는 서버가 팀의 기수로 역조회한다
     * @param userId       요청자 계정 id. 이 팀의 MASTER여야 한다
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void addMember(Long teamId, UUID targetUserId, UUID userId) {
        Long cohortId = accessSupport.requireActiveTeamCohortId(teamId);
        TeamMembership requestMembership =
                accessSupport.requireActiveMembership(cohortId, userId);
        accessSupport.requireMaster(teamId, requestMembership.id());

        validateAccount(targetUserId);
        teamMemberAddition.add(teamId, targetUserId, userId);
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
        // 계속 점유해 그 사람이 어떤 팀에도 다시 못 들어간다.
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

    /**
     * 대상 계정의 존재·미탈퇴를 확인한다 (GR-11).
     *
     * <p>미존재 계정은 {@link IdentityAccountClient}가 계정 미존재 오류로 중단하며,
     * 공개 응답 코드는 {@code TEAM_ACCOUNT_NOT_FOUND}이다. 조회에 성공한 계정은 실제 상태를
     * 반환하며, 탈퇴 상태는 이 Use Case에서 거절한다.</p>
     *
     * <p>계정은 Identity Service 소유라 서비스 간 DB 외래 키가 없다. 즉 이 확인을
     * 건너뛰면 탈퇴 계정도 그대로 팀에 들어간다 — DB가 대신 막아주지 않는다.</p>
     */
    private void validateAccount(UUID targetUserId) {
        IdentityAccountSnapshot snapshot = identityAccountClient.getSnapshot(targetUserId);
        if (snapshot.status() == IdentityAccountState.WITHDRAWN) {
            log.info(
                    "팀원 추가 계정 상태 거부 targetUserId={}, status={}, statusChangedAt={}",
                    targetUserId,
                    snapshot.status(),
                    snapshot.statusChangedAt()
            );
            throw new BusinessException(TeamErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
