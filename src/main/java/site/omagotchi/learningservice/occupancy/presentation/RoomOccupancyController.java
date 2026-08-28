package site.omagotchi.learningservice.occupancy.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.presentation.response.MyOccupancyStatusResponse;
import site.omagotchi.learningservice.occupancy.presentation.response.RoomOccupancyResponse;

import java.util.UUID;

/**
 * 회의실 점유 API.
 */
@RestController
@RequiredArgsConstructor
public class RoomOccupancyController {

    private final RoomOccupancyService roomOccupancyService;
    private final RoomOccupancyLifecycleService roomOccupancyLifecycleService;
    private final OccupancyQueryService occupancyQueryService;

    /**
     * 점유 시작 (MR-01).
     *
     * @return 201. 사용 중이면 409이며, 클라이언트는 그때 공실 알림 신청을 안내한다 (MR-09)
     */
    @PostMapping("/api/v1/spaces/{space-id}/occupancies")
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
     * @return 200. 너무 이르거나 횟수를 다 썼으면 409
     */
    @PostMapping("/api/v1/spaces/{space-id}/occupancies/extend")
    public RoomOccupancyResponse extend(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return RoomOccupancyResponse.from(roomOccupancyLifecycleService.extend(spaceId, requesterId(jwt)));
    }

    /**
     * 반납 (MR-14).
     *
     * @return 204. 이미 종료된 점유면 409
     */
    @PostMapping("/api/v1/spaces/{space-id}/occupancies/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        roomOccupancyLifecycleService.release(spaceId, requesterId(jwt));
    }

    /**
     * 강제 종료 (MR-21). 점유자가 아니라 <b>점유자 기수의 매니저</b>가 호출한다.
     *
     * @return 204. 점유자 기수의 매니저가 아니면 403, 활성 점유가 없으면 409
     */
    @PostMapping("/api/v1/spaces/{space-id}/occupancies/force-release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceRelease(
            @PathVariable("space-id") Long spaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        roomOccupancyLifecycleService.forceRelease(spaceId, requesterId(jwt));
    }

    /**
     * 내가 지금 회의실을 쓰고 있는가 (점유자이거나 참여자).
     *
     * GET /api/v1/occupancies/me
     */
    @GetMapping("/api/v1/occupancies/me")
    public MyOccupancyStatusResponse findMyStatus(@AuthenticationPrincipal Jwt jwt) {
        return MyOccupancyStatusResponse.of(
                occupancyQueryService.isInMeeting(AuthenticatedUser.from(jwt).userId()));
    }

    /**
     * Access JWT의 {@code sub}에서 요청자 계정을 읽는다.
     */
    private static UUID requesterId(Jwt jwt) {
        return AuthenticatedUser.from(jwt).userId();
    }
}
