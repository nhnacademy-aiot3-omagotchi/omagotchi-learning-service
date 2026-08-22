package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PredictionRestClientConfig {

    @Bean
    public RestClient predictionRestClient(
            RestClient.Builder builder,
            PredictionClientProperties properties
    ) {
        return builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
