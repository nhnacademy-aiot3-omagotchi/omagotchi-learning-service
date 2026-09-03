package site.omagotchi.learningservice.team.presentation.response;

import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateStatus;

import java.util.UUID;

/** 팀원 추가 화면의 검색 후보 응답. userId는 추가 명령의 targetUserId로만 사용한다. */
public record TeamMemberCandidateResponse(
        UUID userId,
        String displayName,
        String email,
        TeamMemberCandidateStatus status
) {
    public static TeamMemberCandidateResponse from(TeamMemberCandidateResult result) {
        return new TeamMemberCandidateResponse(
                result.userId(),
                result.displayName(),
                result.email(),
                result.status()
        );
    }
}
