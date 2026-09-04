package site.omagotchi.learningservice.chat.infrastructure;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

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
            HttpClientSettings httpClientSettings,
            ClientHttpConnectorBuilder<?> clientHttpConnectorBuilder,
            ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder
    ) {

        // Boot이 구성한 HTTP 설정을 기반으로 Ollama의 타임아웃만 교체한다.
        // SSL, 리다이렉트, 쿠키, 주소 필터 등의 전역 설정은 유지된다.
        HttpClientSettings ollamaSettings = httpClientSettings
                .withTimeouts(connectTimeout, readTimeout);

        // 저수준 빌더도 Boot 자동구성에서 받아 커넥션 리소스와 커스터마이저를 유지한다.
        // 완성된 커넥터와 팩토리는 빈으로 노출하지 않아 다른 HTTP 클라이언트에는 영향을 주지 않는다.
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .webClientBuilder(webClientBuilder.clientConnector(
                        clientHttpConnectorBuilder.build(ollamaSettings)
                ))
                .restClientBuilder(restClientBuilder.requestFactory(
                        clientHttpRequestFactoryBuilder.build(ollamaSettings)
                ))
                .build();
    }
}
