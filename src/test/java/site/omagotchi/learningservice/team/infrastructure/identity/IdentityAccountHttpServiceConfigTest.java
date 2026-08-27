package site.omagotchi.learningservice.team.infrastructure.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest(
        classes = IdentityAccountHttpServiceConfigTest.TestApplication.class,
        properties = {
                "spring.config.name=identity-account-http-service-config-test",
                "spring.main.web-application-type=none",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                        + "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
                "spring.http.serviceclient.identity-service.base-url=http://localhost:8083",
                "clients.identity.username=learning-service",
                "clients.identity.password=test-only-learning-identity-password"
        }
)
class IdentityAccountHttpServiceConfigTest {

    @Autowired
    private IdentityAccountHttpService httpService;

    @Autowired
    private MockHttpServiceConfiguration mockHttpServiceConfiguration;

    @Test
    @DisplayName("실제 HTTP Service Group 설정의 Learning Basic 인증")
    void appliesLearningBasicCredential() {
        // Given: 실제 Group 설정에 연결된 Identity Mock 응답
        UUID accountId = UUID.randomUUID();
        MockRestServiceServer server = mockHttpServiceConfiguration.server();
        server.expect(once(), requestTo(
                        "http://localhost:8083/api/v1/internal/accounts/" + accountId
                ))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + HttpHeaders.encodeBasicAuth(
                                "learning-service",
                                "test-only-learning-identity-password",
                                StandardCharsets.UTF_8
                        )
                ))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // When & Then: 실제 Interface 호출 실패와 무관한 요청 Header 검증
        assertThatThrownBy(() -> httpService.getAccount(accountId))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(IdentityClientCredentialProperties.class)
    @Import({
            IdentityAccountHttpServiceConfig.class,
            MockHttpServiceConfiguration.class
    })
    static class TestApplication {
    }

    // 실제 HTTP Service Group Builder에 Mock Server 연결
    @TestConfiguration(proxyBeanMethods = false)
    static class MockHttpServiceConfiguration {

        private MockRestServiceServer server;

        @Bean
        RestClientHttpServiceGroupConfigurer identityMockServerConfigurer() {
            return groups -> groups
                    .filterByName(IdentityAccountHttpServiceConfig.GROUP_NAME)
                    .forEachClient((ignoredGroup, builder) ->
                            server = MockRestServiceServer.bindTo(builder).build()
                    );
        }

        MockRestServiceServer server() {
            return Objects.requireNonNull(server, "Identity Mock Server 미등록");
        }
    }
}
