package site.omagotchi.learningservice.realtime.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.realtime.application.RealtimeDestinations;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("WebSocket SUBSCRIBE 인가 인터셉터")
class WebSocketSubscribeAuthorizationInterceptorTest {

    private static final Long COHORT_ID = 3L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    private final CohortAccessService cohortAccessService = mock(CohortAccessService.class);
    private final WebSocketSubscribeAuthorizationInterceptor interceptor =
            new WebSocketSubscribeAuthorizationInterceptor(cohortAccessService);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    @DisplayName("ACTIVE 소속 사용자의 cohort presence topic 구독을 허용한다")
    void allowsActiveMemberToSubscribeCohortPresence() {
        // Given
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID)).willReturn(11L);

        // When
        interceptor.preSend(
                subscribeMessage(RealtimeDestinations.cohortPresenceTopic(COHORT_ID), jwtAuthentication()),
                channel
        );

        // Then
        verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("ACTIVE 소속이 없으면 cohort presence topic 구독을 거부한다")
    void rejectsCohortPresenceSubscriptionWithoutActiveMembership() {
        // Given
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

        // When & Then
        thenThrownBy(() -> interceptor.preSend(
                subscribeMessage(RealtimeDestinations.cohortPresenceTopic(COHORT_ID), jwtAuthentication()),
                channel
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("인증되지 않은 구독을 거부한다")
    void rejectsUnauthenticatedSubscription() {
        // When & Then
        thenThrownBy(() -> interceptor.preSend(
                subscribeMessage(RealtimeDestinations.USER_NOTIFICATIONS_QUEUE, null),
                channel
        )).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("지원하지 않는 destination 구독을 거부한다")
    void rejectsUnsupportedDestination() {
        // When & Then
        thenThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/chat/1", jwtAuthentication()),
                channel
        )).isInstanceOf(AccessDeniedException.class);
        verify(cohortAccessService, never()).requireActiveMembershipId(COHORT_ID, USER_ID);
    }

    @Test
    @DisplayName("인증된 사용자의 개인 알림 큐 구독을 허용한다")
    void allowsAuthenticatedUserNotificationQueue() {
        // When
        interceptor.preSend(
                subscribeMessage(RealtimeDestinations.USER_NOTIFICATIONS_QUEUE, jwtAuthentication()),
                channel
        );

        // Then
        verify(cohortAccessService, never()).requireActiveMembershipId(COHORT_ID, USER_ID);
    }

    private Message<byte[]> subscribeMessage(String destination, JwtAuthenticationToken authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private JwtAuthenticationToken jwtAuthentication() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(TestJwtKeyConfig.USER_ID)
                .claim("role", "USER")
                .build();
        return new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
