package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.presentation.response.RoomOccupancyResponse;

import java.util.UUID;

/**
 * 회의실 점유 API.
 *
 * <p>에러 응답은 이 클래스가 만들지 않는다. 서비스가 던진 {@code BusinessException}을
 * {@code GlobalExceptionHandler}가 받아 {@code ErrorType}에 따라 400/403/404/409로
 * 옮긴다. try-catch도 ResponseEntity 분기도 없다. 출결 조회 불가처럼 클라이언트가
 * 분기할 계약이 없는 기술 실패는 {@code BusinessException}으로 감싸지 않고 그대로
 * 전파해 {@code GlobalExceptionHandler}가 500으로 옮긴다.</p>
 *
 * <p>요청 본문이 없는 것이 의도다 (명세서 02). 기수 식별자를 받으면 출근한 기수와 다른
 * 기수로 점유하는 경로가 열린다 — 점유자 멤버십은 열린 재실 구간에서 도출한다.</p>
 *
 * <p><b>TODO</b>: 요청자 식별을 {@code X-User-Id} 헤더로 받는 것은 인증 파트 연동 전까지의
 * 임시 조치다. 게이트웨이를 거치지 않으면 헤더를 위조할 수 있으므로 그대로 배포하면 안 된다.
 * JWT 인증이 붙으면 {@code TeamController}와 함께 {@code @AuthenticationPrincipal}로 교체한다.</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{space-id}/occupancies")
@RequiredArgsConstructor
public class RoomOccupancyController {

    private final RoomOccupancyService roomOccupancyService;

    /**
     * 점유 시작 (MR-01).
     *
     * @return 201. 사용 중이면 409이며, 클라이언트는 그때 공실 알림 신청을 안내한다 (MR-09)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomOccupancyResponse start(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return RoomOccupancyResponse.from(roomOccupancyService.start(spaceId, userId));
    }
}