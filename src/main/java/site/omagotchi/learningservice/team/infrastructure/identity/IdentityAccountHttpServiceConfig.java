package site.omagotchi.learningservice.team.infrastructure.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import java.nio.charset.StandardCharsets;

// Identity HTTP Service 등록과 Learning 프로세스 인증 설정
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(
        group = IdentityAccountHttpServiceConfig.GROUP_NAME,
        types = IdentityAccountHttpService.class
)
class IdentityAccountHttpServiceConfig {

    static final String GROUP_NAME = "identity-service";

    @Bean
    RestClientHttpServiceGroupConfigurer identityAccountHttpServiceConfigurer(
            IdentityClientCredentialProperties properties
    ) {
        return groups -> groups
                .filterByName(GROUP_NAME)
                .forEachClient((group, builder) -> builder.defaultHeaders(headers ->
                        headers.setBasicAuth(
                                properties.username(),
                                properties.password(),
                                StandardCharsets.UTF_8
                        )
                ));
    }
}
