package site.omagotchi.learningservice.realtime.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;

/**
 * STOMP application destination으로 들어오는 Presence heartbeat를 처리한다.
 */
@Controller
@RequiredArgsConstructor
public class PresenceMessageController {

    private final CohortPresenceService presenceService;

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(
            JwtAuthenticationToken authentication,
            @Header("simpSessionId") String sessionId
    ) {
        presenceService.heartbeat(sessionId, AuthenticatedUser.from(authentication));
    }
}
