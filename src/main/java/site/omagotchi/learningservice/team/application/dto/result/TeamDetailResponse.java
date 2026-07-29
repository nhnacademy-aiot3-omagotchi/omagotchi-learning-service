package site.omagotchi.learningservice.team.application.dto.result;

import site.omagotchi.learningservice.team.domain.Team;

import java.time.OffsetDateTime;
import java.util.List;

public record TeamDetailResponse (
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt,
        int memberCount,
        List<TeamMemberResponse> members
){
    public static TeamDetailResponse of(Team team, List<TeamMemberResponse> members) {
        return new TeamDetailResponse(
                team.getId(), team.getCohortId(), team.getName(),
                team.getCreatedAt(), members.size(), members
        );
    }
}
