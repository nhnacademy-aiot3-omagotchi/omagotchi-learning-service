package site.omagotchi.learningservice.team.presentation.response;

import site.omagotchi.learningservice.team.application.result.TeamMemberResult;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;

/**
 * 팀원 응답. 내부 식별자(user_id, cohort_membership_id)를 노출하지 않는다 (GR-15).
 *
 * <p>{@code memberId}는 {@code team_members.id}로 팀 모듈이 소유한 식별자이며,
 * 제외·위임 요청의 대상 지정에 그대로 쓰인다.</p>
 *
 * <p>{@code displayName}은 null일 수 있다 — 멤버십은 남았지만 Identity의 일괄 조회
 * 결과에 계정이 없는 경우다. 그때도 행을 빼지 않는다는 판단은
 * {@link TeamMemberResult}에 적혀 있다.</p>
 */
public record TeamMemberResponse(
        Long memberId,
        String displayName,
        TeamMemberRole role,
        OffsetDateTime joinedAt
) {

    public static TeamMemberResponse from(TeamMemberResult result) {
        return new TeamMemberResponse(
                result.memberId(),
                result.displayName(),
                result.role(),
                result.joinedAt()
        );
    }
}
