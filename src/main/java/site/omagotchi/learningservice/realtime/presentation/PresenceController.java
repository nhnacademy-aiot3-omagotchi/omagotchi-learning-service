package site.omagotchi.learningservice.realtime.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.realtime.application.CohortPresenceSnapshot;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;
import site.omagotchi.learningservice.realtime.application.PresenceErrorCode;

/**
 * 현재 사용자가 속한 ACTIVE cohort의 Presence를 REST로 등록·갱신·조회한다.
 *
 * <p>Presence는 WebSocket 연결이 아니라 주기적인 REST heartbeat와 Redis TTL로 유지한다.
 * 재실 현황은 초 단위 실시간성이 필요하지 않으므로 View를 stateless로 유지하는 편을 택했다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/me/presence")
public class PresenceController {

    /**
     * 호출자가 소유한 Presence 세션 식별자.
     *
     * <p>Browser가 아니라 View(BFF)가 생성해 Session에 보관하고 하류 호출에만 싣는다.
     * Browser 입력으로 받으면 남의 재실 세션을 조작할 수 있으므로 노출하지 않는다.
     */
    public static final String PRESENCE_SESSION_HEADER = "X-Presence-Session";

    private final CohortPresenceService presenceService;

    @GetMapping
    public CohortPresenceSnapshot getMyCohortPresence(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return presenceService.currentUserSnapshot(user.userId());
    }

    /**
     * REST heartbeat. 최초 호출은 세션 등록, 이후 호출은 TTL 연장으로 동작한다.
     *
     * <p>응답에 snapshot을 함께 실어 화면이 조회를 위해 한 번 더 왕복하지 않게 한다.
     */
    @PostMapping("/heartbeat")
    public CohortPresenceSnapshot heartbeat(
            JwtAuthenticationToken authentication,
            @RequestHeader(PRESENCE_SESSION_HEADER) String presenceSessionId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        presenceService.heartbeat(requireSessionId(presenceSessionId), user);
        return presenceService.currentUserSnapshot(user.userId());
    }

    /**
     * 이탈 통지. TTL 만료를 기다리지 않고 즉시 제거한다.
     *
     * <p>이 호출이 실패해도 heartbeat가 멈추면 TTL로 결국 정리되므로,
     * 화면은 이 응답을 기다리거나 재시도할 필요가 없다.
     */
    @DeleteMapping
    public ResponseEntity<Void> leave(
            JwtAuthenticationToken authentication,
            @RequestHeader(PRESENCE_SESSION_HEADER) String presenceSessionId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        presenceService.disconnectSession(requireSessionId(presenceSessionId), user.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * CohortPresenceService는 빈 sessionId를 조용히 무시하고 반환한다.
     * 그대로 두면 heartbeat가 아무것도 등록하지 않은 채 200을 반환해 장애가 숨는다.
     *
     * <p>Header 자체가 없는 경우는 MissingRequestHeaderException이 400으로 변환하므로
     * 여기서는 공백 값만 막는다.
     */
    private static String requireSessionId(String presenceSessionId) {
        if (presenceSessionId == null || presenceSessionId.isBlank()) {
            throw new BusinessException(PresenceErrorCode.SESSION_ID_REQUIRED);
        }
        return presenceSessionId;
    }
}
