package site.omagotchi.learningservice.occupancy.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantQueryService;
import site.omagotchi.learningservice.occupancy.presentation.request.AddParticipantRequest;
import site.omagotchi.learningservice.occupancy.presentation.response.OccupancyParticipantResponse;
import site.omagotchi.learningservice.occupancy.presentation.response.ParticipantCandidateResponse;

import java.util.List;
import java.util.UUID;

/**
 * 회의 참여자 API.
 *
 * <p>이탈과 제외가 같은 엔드포인트인 것이 의도다. 결과가 완전히 같고 요청자와 대상이
 * 같은지로만 갈리므로, 경로를 나누면 클라이언트가 "내가 점유자인가"를 먼저 판단해
 * 호출을 골라야 한다. 자기 자신을 지정하면 이탈, 남을 지정하면 제외다.</p>
 *
 * <p>에러 응답은 이 클래스가 만들지 않는다. 서비스가 던진 {@code BusinessException}을
 * {@code GlobalExceptionHandler}가 받아 400/403/404/409로 옮긴다.</p>
 *
 * <p><b>요청자는 Access JWT에서만 읽는다.</b> 헤더로 받으면 게이트웨이를 우회한 요청이
 * 점유자를 사칭해 남을 회의에서 내보낼 수 있다 — 이 API는 요청자와 대상이 같은지로
 * 이탈·제외를 가르므로 요청자 위조가 곧 권한 위조다.</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{space-id}/occupancies/participants")
@RequiredArgsConstructor
public class OccupancyParticipantController {

    private final OccupancyParticipantService occupancyParticipantService;
    private final OccupancyParticipantQueryService occupancyParticipantQueryService;

    @GetMapping
    public List<OccupancyParticipantResponse> getParticipants(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return occupancyParticipantQueryService.getParticipants(spaceId, requesterId(jwt)).stream()
                .map(OccupancyParticipantResponse::from)
                .toList();
    }

    @GetMapping("/candidates")
    public List<ParticipantCandidateResponse> searchCandidates(
            @PathVariable("space-id") Long spaceId,
            @RequestParam String query,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return occupancyParticipantQueryService
                .searchCandidates(spaceId, query, requesterId(jwt)).stream()
                .map(ParticipantCandidateResponse::from)
                .toList();
    }

    /**
     * 참여자 추가 (MR-19, MR-28, MR-33). 점유자만 호출할 수 있고 수락 절차는 없다.
     *
     * @return 201. 이미 이탈했던 사람을 다시 추가하면 기존 행이 복원되고 같은 201이다
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void add(
            @PathVariable("space-id") Long spaceId,
            @Valid @RequestBody AddParticipantRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        occupancyParticipantService.add(spaceId, request.targetUserId(), requesterId(jwt));
    }

    /**
     * 이탈·제외 (MR-31).
     *
     * @param targetUserId 요청자 본인이면 이탈, 다른 사람이면 점유자의 제외다.
     *                     점유자를 지정하면 400 — 반납으로만 종료할 수 있다
     */
    @DeleteMapping("/{target-user-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable("space-id") Long spaceId,
            @PathVariable("target-user-id") UUID targetUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        occupancyParticipantService.remove(spaceId, targetUserId, requesterId(jwt));
    }

    /**
     * Access JWT의 {@code sub}에서 요청자 계정을 읽는다.
     *
     * <p>{@code null} 검사를 두지 않는 것이 의도다. 이 컨트롤러의 모든 경로가
     * {@code SecurityConfig}의 {@code anyRequest().authenticated()}에 걸려 있어 인증 없이는
     * 도달하지 않는다 — 여기서 {@code null}이면 보안 설정이 뚫린 것이고, 조용히 넘기는 것보다
     * 예외로 드러나는 편이 낫다.</p>
     */
    private static UUID requesterId(Jwt jwt) {
        return AuthenticatedUser.from(jwt).userId();
    }
}
