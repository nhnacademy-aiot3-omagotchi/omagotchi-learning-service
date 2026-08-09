package site.omagotchi.learningservice.team.presentation.response;

import site.omagotchi.learningservice.team.application.result.TeamDetailResult;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 팀 상세와 팀원 목록 응답 (GR-06, GR-15). 그 팀 소속자에게만 반환된다.
 *
 * <p>{@code memberCount}는 별도 카운트가 아니라 목록에서 파생된 값이다 —
 * 목록과 숫자가 어긋나면 "8명인데 7명만 보인다"는 상태가 사용자에게 보인다.</p>
 *
 * <p>팀원은 마스터가 먼저, 그다음 가입 순으로 정렬되어 있다. 여기서 다시 정렬하지 않는다.</p>
 */
public record TeamDetailResponse(
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt,
        int memberCount,
        List<TeamMemberResponse> members
) {

    public static TeamDetailResponse from(TeamDetailResult result) {
        return new TeamDetailResponse(
                result.teamId(),
                result.cohortId(),
                result.name(),
                result.createdAt(),
                result.memberCount(),
                result.members().stream()
                        .map(TeamMemberResponse::from)
                        .toList()
        );
    }
}
