package site.omagotchi.learningservice.team.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.dto.result.TeamDetailResponse;
import site.omagotchi.learningservice.team.application.dto.result.TeamResponse;

import java.util.List;
import java.util.UUID;

/**
 * 팀 API.
 *
 * <p>에러 응답은 이 클래스가 직접 만들지 않는다. 서비스가 던진
 * {@code BusinessException}을 {@code GlobalExceptionHandler}가 받아
 * {@code ErrorType}에 따라 400/403/404/409로 옮긴다. 그래서 여기에는
 * try-catch도, ResponseEntity 분기도 없다.</p>
 *
 * <p><b>TODO</b>: 요청자 식별을 {@code X-User-Id} 헤더로 받는 것은 인증 파트 연동 전까지의
 * 임시 조치다. 게이트웨이를 거치지 않으면 헤더를 위조할 수 있으므로 그대로 배포하면 안 된다.
 * JWT 인증이 붙으면 이 파라미터는 {@code @AuthenticationPrincipal}로 교체된다.</p>
 */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final TeamMasterService teamMasterService;

    /**
     * 팀 생성 (GR-01, GR-02).
     *
     * <p>본문의 {@code cohortId}는 생략할 수 있다 (RM-28). 활성 기수가 하나면 서버가 정하고,
     * 둘 이상이면 400으로 지정을 요구한다. 서버는 이 값을 신뢰하지 않고 요청자의
     * 활성 멤버십인지 반드시 검증한다.</p>
     *
     * <p>이름 길이·공백 규칙에 Bean Validation을 걸지 않은 것은 의도다 —
     * {@link CreateTeamRequest} 주석 참고.</p>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return teamService.create(request, userId);
    }

    /**
     * 내가 속한 팀 목록 (GR-06).
     *
     * <p>여러 기수를 담당하는 매니저·멘토는 기수별로 하나씩, 복수 건이 정상이다.
     * 소속이 없으면 404가 아니라 빈 배열을 준다 — "없음"은 오류가 아니다.</p>
     */
    @GetMapping("/me")
    public List<TeamResponse> getMyTeams(@RequestHeader("X-User-Id") UUID userId) {
        return teamService.getMyTeams(userId);
    }

    /**
     * 팀 상세와 팀원 목록 (GR-15). 그 팀 소속자만 조회할 수 있다.
     *
     * <p>응답에는 내부 식별자(user_id, cohort_membership_id)가 없다. 팀원은 팀 모듈이
     * 소유한 {@code memberId}로 식별되며, 제외 요청에 그 값을 그대로 쓰면 된다.</p>
     */
    @GetMapping("/{team-id}")
    public TeamDetailResponse getTeam(
            @PathVariable("team-id") Long teamId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return teamService.getTeam(teamId, userId);
    }

    /**
     * 팀원 추가 (GR-03, GR-04). MASTER만 호출할 수 있고 수락 절차 없이 즉시 반영된다.
     *
     * <p>대상은 계정 id로 지정한다. 어느 기수의 멤버십으로 넣을지는 클라이언트가 정하지 않고,
     * 서버가 팀의 기수로 대상의 활성 멤버십을 역조회한다 — 그 조회가 실패하는 것이
     * 곧 기수 불일치(GR-22)다.</p>
     */
    @PostMapping("/{team-id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(
            @PathVariable("team-id") Long teamId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddTeamMemberRequest request
            ) {
        teamMemberService.addMember(teamId, request, userId);
    }

    /**
     * 팀원 제외 (GR-05). MASTER만 호출할 수 있다.
     *
     * <p>{@code memberId}는 계정 id도 멤버십 id도 아닌 {@code team_members.id}다.
     * 팀 상세 조회 응답의 {@code memberId}를 그대로 넣으면 된다.</p>
     *
     * <p>MASTER 본인은 제외 대상이 될 수 없다(400). 마스터가 팀을 떠나려면 위임 후 탈퇴,
     * 또는 단독일 때 탈퇴(=팀 해체)를 거쳐야 한다.</p>
     */
    @DeleteMapping("/{team-id}/members/{member-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kickMember(
            @PathVariable("team-id") Long teamId,
            @PathVariable("member-id") Long memberId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMemberService.kickMember(teamId, memberId, userId);
    }

    /**
     * 탈퇴 (GR-07, GR-08, GR-13). 본인 의사로만 나갈 수 있다.
     *
     * <p>마스터의 결과가 상황에 따라 갈린다. 팀원이 남아 있으면 409로 거부하고(위임 먼저),
     * 단독 팀원이면 탈퇴와 동시에 팀이 소프트 삭제된다.</p>
     *
     * <p>{@code DELETE /members/me}를 쓰지 않는 이유는 memberId가 Long이라
     * "me" 리터럴이 경로 변환에서 깨지기 때문이다.</p>
     */
    @PostMapping("/{team-id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @PathVariable("team-id") Long teamId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMemberService.leave(teamId, userId);
    }

    /**
     * 마스터 위임 (GR-14, GR-20). MASTER만 수행할 수 있다.
     *
     * <p>상태 전이라 경로 마지막이 동사형이다 (09-rest-api-convention). 대상을 경로에 두는
     * 것은 팀원 제외({@code DELETE /members/{member-id}})와 대칭을 맞추기 위해서다 —
     * 둘 다 "특정 팀원에게 하는 일"이다.</p>
     *
     * <p>{@code memberId}는 {@code team_members.id}다. 팀 상세 조회 응답의 값을 그대로 넣는다.</p>
     *
     * @return 204. 자기 자신에게 위임하면 400, 대상이 이 팀 소속이 아니면 404
     */
    @PostMapping("/{team-id}/members/{member-id}/delegate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delegate(
            @PathVariable("team-id") Long teamId,
            @PathVariable("member-id") Long memberId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMasterService.delegate(teamId, memberId, userId);
    }

    /**
     * 팀 해체 (GR-19). MASTER만 수행할 수 있다.
     *
     * <p>{@code DELETE}인 이유는 클라이언트 입장에서 리소스가 사라지기 때문이다. 실제로는
     * 팀 소프트 삭제 + 팀원 물리 삭제지만, 목록·상세 조회가 해체된 팀을 걸러내므로
     * 외부에서는 제거와 구분되지 않는다. 해체 후 같은 이름으로 다시 만들 수도 있다.</p>
     *
     * <p>점유 반납을 {@code POST /release}로 한 것과 다른 판단이다 — 점유 행은 종료 후에도
     * 통계 원천으로 조회되지만, 팀은 조회 경로에서 완전히 빠진다.</p>
     *
     * @return 204. MASTER가 아니면 403
     */
    @DeleteMapping("/{team-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disband(
            @PathVariable("team-id") Long teamId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        teamMasterService.disband(teamId, userId);
    }
}
