package site.omagotchi.learningservice.team.application.result;

import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;

/**
 * 내부 식별자(user_id, cohort_membership_id)는 담지 않는다 (GR-15).
 * memberId는 team_members.id로 팀 모듈이 소유한 식별자이며,
 * 제외·위임 요청의 대상 지정에 쓰인다.
 */
public record TeamMemberResult(
        Long memberId,
        String displayName,
        TeamMemberRole role,
        OffsetDateTime joinedAt
) {
    /**
     * @param displayName Identity Service 조회 결과. <b>null일 수 있다</b> —
     *                    멤버십은 남았지만 Identity의 일괄 조회 결과에 계정이 없는 경우다.
     *                    이때도 팀원 목록에서 행을 빼지 않는다. 소속의 진실은
     *                    {@code team_members} 행이고, 표시명은 부가 정보이기 때문이다.
     *                    Identity 연결·서버 오류는 이 값으로 숨기지 않고 요청을 503으로 실패시킨다.
     */
    public static TeamMemberResult of(TeamMember member, String displayName) {
        return new TeamMemberResult(
                member.getId(),
                displayName,
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
