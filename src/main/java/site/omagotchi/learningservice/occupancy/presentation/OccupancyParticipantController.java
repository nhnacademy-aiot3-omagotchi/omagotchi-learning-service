package site.omagotchi.learningservice.occupancy.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.presentation.request.AddParticipantRequest;

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
 * <p><b>TODO</b>: {@code X-User-Id} 헤더는 인증 파트 연동 전까지의 임시 조치다.
 * 게이트웨이를 거치지 않으면 위조할 수 있으므로 그대로 배포하면 안 된다
 * ({@code RoomOccupancyController}와 함께 {@code @AuthenticationPrincipal}로 교체한다).</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{space-id}/occupancies/participants")
@RequiredArgsConstructor
public class OccupancyParticipantController {

    private final OccupancyParticipantService occupancyParticipantService;

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
            @RequestHeader("X-User-Id") UUID userId
    ) {
        occupancyParticipantService.add(spaceId, request.targetUserId(), userId);
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
            @RequestHeader("X-User-Id") UUID userId
    ) {
        occupancyParticipantService.remove(spaceId, targetUserId, userId);
    }
}
