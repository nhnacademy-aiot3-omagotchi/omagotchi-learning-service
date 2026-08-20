package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
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
 * <p><b>요청자는 Access JWT에서만 읽는다.</b> 헤더로 받으면 게이트웨이를 우회한 요청이
 * 남의 계정을 사칭해 회의실을 잡거나 남의 점유를 반납할 수 있다. 게이트웨이도 같은 이유로
 * 들어오는 {@code X-User-Id}를 제거하므로(default-filters), 헤더에 의존하면 정상 경로에서는
 * 오히려 동작하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{space-id}/occupancies")
@RequiredArgsConstructor
public class RoomOccupancyController {

    private final RoomOccupancyService roomOccupancyService;
    private final RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    /**
     * 점유 시작 (MR-01).
     *
     * @return 201. 사용 중이면 409이며, 클라이언트는 그때 공실 알림 신청을 안내한다 (MR-09)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomOccupancyResponse start(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return RoomOccupancyResponse.from(roomOccupancyService.start(spaceId, requesterId(jwt)));
    }

    /**
     * 연장 (MR-06).
     *
     * <p>상태 전이라 경로 마지막이 동사형이다 (09-rest-api-convention). 200에 본문을
     * 싣는 것은 클라이언트가 새 {@code expiresAt}과 {@code remainingSeconds}로 타이머를
     * 다시 맞춰야 하기 때문이다.</p>
     *
     * @return 200. 너무 이르거나 횟수를 다 썼으면 409
     */
    @PostMapping("/extend")
    public RoomOccupancyResponse extend(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return RoomOccupancyResponse.from(roomOccupancyLifecycleService.extend(spaceId, requesterId(jwt)));
    }

    /**
     * 반납 (MR-14).
     *
     * <p>{@code DELETE}가 아닌 이유는 점유 행이 사라지지 않기 때문이다 — 종료 상태로
     * 전이할 뿐 행은 통계 원천으로 보존된다.</p>
     *
     * @return 204. 이미 종료된 점유면 409
     */
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        roomOccupancyLifecycleService.release(spaceId, requesterId(jwt));
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
