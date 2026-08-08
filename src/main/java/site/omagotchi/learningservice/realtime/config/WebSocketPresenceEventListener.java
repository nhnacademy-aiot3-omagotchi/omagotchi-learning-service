package site.omagotchi.learningservice.realtime.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;

/**
 * Spring WebSocket session disconnect event를 Redis Presence cleanup으로 연결한다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketPresenceEventListener {

    private final CohortPresenceService presenceService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof JwtAuthenticationToken authentication) {
            presenceService.disconnectSession(event.getSessionId(), java.util.UUID.fromString(authentication.getName()));
            return;
        }
        presenceService.disconnectSession(event.getSessionId(), null);
    }
}
