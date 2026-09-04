package site.omagotchi.learningservice.chat.infrastructure;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.client.reactive.ReactorClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Objects;

/**
 * Ollama 호출에만 긴 읽기 타임아웃을 건다
 * 전역 spring.http.clients.read-timeout은 identity-service 호출에 맞춘 짧은 값이다
 * Spring AI는 OllamaApiAutoConfiguration은 컨텍스트의 RestClient, WebClient 빌더를 그대로 받아 쓰기 때문에 그 값이 Ollama 호출에도 걸려, 첫 토큰이 나오기 전에 끊긴다
 * <p>
 * Gemini는 여기에 해당하지 않는다. ChatClientConfig가 Google SDK의 Client를 직접 만들어 넘기므로 Boot의 HTTP 설정을 타지 않는다
 * OllamaApi Bean을 직접 등록하면 자동구성이 ConditionalOnMissingBean으로 꺼진다
 */
@Configuration
public class OllamaApiConfig {

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public OllamaApiConfig(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${ollama.connect-timeout}") Duration connectTimeout,
            @Value("${ollama.read-timeout}") Duration readTimeout
    ) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Bean
    public OllamaApi ollamaApi(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectProvider<ReactorResourceFactory> reactorResourceFactory
    ) {

        // 커넥터, 팩토리를 빈으로 노출하지 않는다
        // 빈으로 두면 Boot의 전역 커넥터가 @ConditionalOnMissingBean으로 물러나고, 그것이 모든 클라이언트에 적용되어 identity 호출까지 이 값을 쓰게 된다.
        // 연결 타임아웃도 함께 넘긴다. defaults()는 전부 비어 있어서,
        // 읽기만 채우면 전역 설정으로 걸려 있던 연결 제한이 함께 사라진다
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(connectTimeout, readTimeout);

        // 채팅 스트리밍(streamingChat)은 WebClient를 탄다. 여기가 실제로 막히던 경로다
        // 커넥션 풀, 이벤트 루프는 Boot이 관리하는 것을 그대로 쓴다
        // 넘기지 않으면 reactor-netty 전역 리소스를 쓰게 되어 이 클라이언트만 컨텍스트 생명주기 밖에 놓인다
        ReactorClientHttpConnectorBuilder connectorBuilder = ClientHttpConnectorBuilder.reactor();
        ReactorResourceFactory resourceFactory = reactorResourceFactory.getIfAvailable();
        if (Objects.nonNull(resourceFactory)) {
            connectorBuilder = connectorBuilder.withReactorResourceFactory(resourceFactory);
        }

        // 비스트리밍 chat()과 모델 조회는 RestClient를 탄다
        // 지금 채팅은 쓰지 않지만, 넘기지 않으면 아무 제한 없는 빌더가 기본값으로 들어간다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        // 빌더는 컨텍스트 것을 받아 커넥터·팩토리만 덮어쓴다
        // 새로 만들면(WebClient.builder()) Boot이 등록한 커스터마이저가 빠져 Observation과 추적 컨텍스트 전파가 이 호출에서만 사라진다
        // 두 빌더 모두 프로토타입 빈이라 여기서 변형해도 다른 클라이언트에 영향이 없다
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .webClientBuilder(webClientBuilder.clientConnector(connectorBuilder.build(settings)))
                .restClientBuilder(restClientBuilder.requestFactory(requestFactory))
                .build();
    }
}
