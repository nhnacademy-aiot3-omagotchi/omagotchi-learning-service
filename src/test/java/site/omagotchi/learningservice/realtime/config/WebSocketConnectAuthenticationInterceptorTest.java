package site.omagotchi.learningservice.realtime.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.security.Principal;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WebSocket CONNECT 인증 인터셉터")
class WebSocketConnectAuthenticationInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    private final WebSocketConnectAuthenticationInterceptor interceptor =
            new WebSocketConnectAuthenticationInterceptor(jwtDecoder, jwtAuthenticationConverter);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    @DisplayName("CONNECT Bearer 토큰을 검증하고 Principal을 설정한다")
    void authenticatesConnectWithBearerToken() {
        // Given
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(TestJwtKeyConfig.USER_ID)
                .claim("role", "USER")
                .build();
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);

        // When
        Message<?> result = interceptor.preSend(connectMessage("Bearer access-token"), channel);

        // Then
        Principal principal = StompHeaderAccessor.wrap(result).getUser();
        then(principal).isInstanceOf(Authentication.class);
        then(principal.getName()).isEqualTo(TestJwtKeyConfig.USER_ID);
        verify(jwtDecoder).decode("access-token");
    }

    @Test
    @DisplayName("CONNECT Authorization 헤더가 없으면 거부한다")
    void rejectsConnectWithoutBearerToken() {
        // When & Then
        thenThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("CONNECT JWT 검증 실패를 인증 실패로 처리한다")
    void rejectsInvalidToken() {
        // Given
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        // When & Then
        thenThrownBy(() -> interceptor.preSend(connectMessage("Bearer bad-token"), channel))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
