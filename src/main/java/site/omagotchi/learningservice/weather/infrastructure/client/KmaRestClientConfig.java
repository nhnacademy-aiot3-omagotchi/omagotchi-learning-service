package site.omagotchi.learningservice.weather.infrastructure.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class KmaRestClientConfig {

    @Bean
    public RestClient kmaRestClient(RestClient.Builder builder, KmaProperties kmaProperties) {
        Duration timeout = kmaProperties.requestTimeout();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        return builder
                .baseUrl(kmaProperties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
