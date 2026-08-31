package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Configuration
public class PredictionRestClientConfig {

    @Bean
    public RestClient predictionRestClient(
            RestClient.Builder builder,
            PredictionClientProperties properties,
            PredictionClientCredentialProperties credentials
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                // 관계 전용 Credential은 모든 예측 요청에 동일하게 적용되므로 기본 헤더로 둔다
                .defaultHeaders(headers -> headers.setBasicAuth(
                        credentials.username(),
                        credentials.password(),
                        StandardCharsets.UTF_8
                ))
                .build();
    }
}
