package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;
import site.omagotchi.learningservice.community.infrastructure.CommunityAttachmentProperties;
import site.omagotchi.learningservice.realtime.application.PresenceProperties;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import java.time.Duration;

import static org.assertj.core.api.BDDAssertions.then;

class ServiceDefaultsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "spring.config.location=classpath:/application.yaml",
                    "COMMUNITY_ATTACHMENT_BUCKET=test-bucket",
                    "TELEGRAM_BOT_USERNAME=test-bot",
                    "TELEGRAM_BOT_TOKEN=test-only-token",
                    "TELEGRAM_WEBHOOK_SECRET=test-only-webhook-secret"
            )
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("외부 접속 설정만 주입한 상태의 첨부파일·타이머·Telegram 기본값 적용")
    void bindsDefaultsWithoutPolicyEnvironmentVariables() {
        // Given: 별도 정책 환경변수 없는 서비스 기본 설정
        // When
        contextRunner.run(context -> {
            // Then: 파일 한도의 계층 간 일치와 요청 크기 안의 전체 첨부파일 수용
            then(context).hasNotFailed();
            Binder binder = Binder.get(context.getEnvironment());
            CommunityAttachmentProperties attachments = context.getBean(CommunityAttachmentProperties.class);
            then(binder.bind("spring.servlet.multipart.max-file-size", DataSize.class).get())
                    .isEqualTo(attachments.maxFileSize());
            then(binder.bind("spring.servlet.multipart.max-request-size", DataSize.class).get().toBytes())
                    .isGreaterThan(attachments.maxFileSize().toBytes() * attachments.maxCount());
            then(binder.bind("timer.max-duration", Duration.class).get()).isPositive();
            then(context.getBean(PresenceProperties.class).sessionTtl()).isPositive();
            then(context.getBean(TelegramProperties.class).linkToken().ttl()).isPositive();
        });
    }

    @Test
    @DisplayName("기존 첨부파일 환경변수 변경의 Servlet·도메인 설정 동시 적용")
    void preservesLegacyEnvironmentOverrides() {
        // Given: 기본값과 다른 기존 환경변수
        // When
        contextRunner.withPropertyValues("COMMUNITY_ATTACHMENT_MAX_FILE_SIZE=4MB")
                .run(context -> {
                    // Then
                    then(context).hasNotFailed();
                    then(context.getBean(CommunityAttachmentProperties.class).maxFileSize())
                            .isEqualTo(DataSize.ofMegabytes(4));
                    then(Binder.get(context.getEnvironment())
                            .bind("spring.servlet.multipart.max-file-size", DataSize.class).get())
                            .isEqualTo(DataSize.ofMegabytes(4));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({CommunityAttachmentProperties.class, PresenceProperties.class,
            TelegramProperties.class})
    static class PropertiesConfig {
    }
}
