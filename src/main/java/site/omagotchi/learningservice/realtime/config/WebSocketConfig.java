package site.omagotchi.learningservice.realtime.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import site.omagotchi.learningservice.realtime.application.RealtimeDestinations;

/**
 * Presence와 향후 realtime 기능이 공유하는 Spring WebSocket/STOMP 기반 설정이다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketConnectAuthenticationInterceptor connectAuthenticationInterceptor;
    private final WebSocketSubscribeAuthorizationInterceptor subscribeAuthorizationInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(RealtimeDestinations.WEBSOCKET_ENDPOINT);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(RealtimeDestinations.TOPIC_PREFIX, RealtimeDestinations.QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(RealtimeDestinations.APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(RealtimeDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(connectAuthenticationInterceptor, subscribeAuthorizationInterceptor);
    }
}
