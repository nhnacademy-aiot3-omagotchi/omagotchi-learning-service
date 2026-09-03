package site.omagotchi.learningservice.team.application.result;

import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Identity 표시명을 붙이기 전, Learning이 보유한 팀 상세 조회 결과.
 *
 * <p>{@code userId}는 Identity 표시 이름 조회를 위한 내부 논리 참조다. 최종
 * {@link TeamDetailResult}에는 복사하지 않으며 HTTP 응답에도 노출하지 않는다.</p>
 */
public record TeamDetailLocalResult(
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt,
        Long myMemberId,
        TeamMemberRole myRole,
        List<Member> members
) {
    public TeamDetailLocalResult {
        members = List.copyOf(members);
    }

    public static TeamDetailLocalResult of(
            Team team,
            TeamMember requesterMember,
            List<Member> members
    ) {
        return new TeamDetailLocalResult(
                team.getId(),
                team.getCohortId(),
                team.getName(),
                team.getCreatedAt(),
                requesterMember.getId(),
                requesterMember.getRole(),
                members
        );
    }

    public record Member(
            Long memberId,
            UUID userId,
            TeamMemberRole role,
            OffsetDateTime joinedAt
    ) {
        public static Member from(TeamMember member, UUID userId) {
            return new Member(
                    member.getId(),
                    userId,
                    member.getRole(),
                    member.getJoinedAt()
            );
        }
    }
}
