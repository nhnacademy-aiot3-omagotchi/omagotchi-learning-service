package site.omagotchi.learningservice.environment.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;

import java.time.Duration;

@Configuration
public class IotRestClientConfig {

    @Bean
    public RestClient iotRestClient(RestClient.Builder builder, EnvironmentProperties properties){
        Duration timeout = properties.iot().requestTimeout();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());

        return builder.requestFactory(factory).build();
    }
}