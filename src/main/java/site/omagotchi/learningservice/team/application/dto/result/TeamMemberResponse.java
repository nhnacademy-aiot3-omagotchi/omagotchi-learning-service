package site.omagotchi.learningservice.team.application.dto.result;

import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;

/**
 * 내부 식별자(user_id, cohort_membership_id)는 노출하지 않는다 (GR-15).
 * memberId는 team_members.id로 팀 모듈이 소유한 식별자이며,
 * 제외·위임 요청의 대상 지정에 쓰인다.
 */
public record TeamMemberResponse (
        Long memberId,
        String displayName,
        TeamMemberRole role,
        OffsetDateTime joinedAt
){
    public static TeamMemberResponse of(TeamMember member, String displayName) {
        return new TeamMemberResponse(
                member.getId(),
                displayName,
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
