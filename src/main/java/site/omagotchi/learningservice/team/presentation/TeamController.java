package site.omagotchi.learningservice.team.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.dto.result.TeamDetailResponse;
import site.omagotchi.learningservice.team.application.dto.result.TeamResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final TeamMemberService teamMemberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return teamService.create(request, userId);
    }

    @GetMapping("/me")
    public List<TeamResponse> getMyTeams(@RequestHeader("X-User-Id") UUID userId) {
        return teamService.getMyTeams(userId);
    }

    @GetMapping("/{teamId}")
    public TeamDetailResponse getTeam(
            @PathVariable Long teamId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return teamService.getTeam(teamId, userId);
    }

    /** 팀원 추가 (GR-03)*/
    @PostMapping("{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(
            @PathVariable Long teamId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddTeamMemberRequest request
            ) {
        teamMemberService.addMember(teamId, request, userId);
    }

    /** 팀원 제외 (GR-05) */
    @DeleteMapping("/{teamId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kickMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMemberService.kickMember(teamId, memberId, userId);
    }

    /**
     * 탈퇴 (GR-07).
     * DELETE /members/me 를 쓰지 않는 이유는 memberId가 Long이라
     * "me" 리터럴이 경로 변환에서 깨지기 때문이다.
     */
    @PostMapping("/{teamId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @PathVariable Long teamId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMemberService.leave(teamId, userId);
    }
}
