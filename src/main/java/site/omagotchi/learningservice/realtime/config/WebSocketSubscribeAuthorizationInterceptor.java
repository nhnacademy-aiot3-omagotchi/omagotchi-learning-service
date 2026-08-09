package site.omagotchi.learningservice.realtime.config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.realtime.application.RealtimeDestinations;

import java.security.Principal;

/**
 * STOMP SUBSCRIBE 프레임을 서버 측 권한으로 제한한다.
 *
 * <p>cohort presence topic은 JWT 사용자에게 해당 cohort의 ACTIVE membership이 있을 때만 허용한다.</p>
 */
@Component
@RequiredArgsConstructor
public class WebSocketSubscribeAuthorizationInterceptor implements ChannelInterceptor {

    private final CohortAccessService cohortAccessService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        JwtAuthenticationToken authentication = requireJwtAuthentication(accessor.getUser());
        String destination = accessor.getDestination();
        if (RealtimeDestinations.USER_NOTIFICATIONS_QUEUE.equals(destination)) {
            return message;
        }

        Long cohortId = RealtimeDestinations.cohortIdFromPresenceTopic(destination)
                .orElseThrow(() -> new AccessDeniedException("Unsupported subscription destination"));
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        try {
            cohortAccessService.requireActiveMembershipId(cohortId, user.userId());
        } catch (BusinessException exception) {
            throw new AccessDeniedException("Cohort subscription is not authorized", exception);
        }

        return message;
    }

    private JwtAuthenticationToken requireJwtAuthentication(Principal principal) {
        if (principal instanceof JwtAuthenticationToken authentication && authentication.isAuthenticated()) {
            return authentication;
        }
        throw new AuthenticationCredentialsNotFoundException("WebSocket authentication is required");
    }
}
