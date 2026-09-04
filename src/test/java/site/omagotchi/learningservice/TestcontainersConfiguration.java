package site.omagotchi.learningservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountSnapshot;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@TestConfiguration(proxyBeanMethods = false)
@Import(TestJwtKeyConfig.class)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.1"));
    }

    // 전체 통합 테스트가 별도 Identity 프로세스에 의존하지 않도록 하는 테스트 경계
    @Bean
    @Primary
    IdentityAccountClient testIdentityAccountClient() {
        return new IdentityAccountClient() {
            @Override
            public IdentityAccountSnapshot getSnapshot(UUID userId) {
                return new IdentityAccountSnapshot(
                        IdentityAccountState.ACTIVE,
                        Instant.EPOCH
                );
            }

            @Override
            public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
                return userIds.stream()
                        .distinct()
                        .collect(Collectors.toUnmodifiableMap(
                                userId -> userId,
                                UUID::toString
                        ));
            }

            @Override
            public List<IdentityAccountView> search(String query, Collection<UUID> candidateIds) {
                return List.of();
            }
        };
    }

}
