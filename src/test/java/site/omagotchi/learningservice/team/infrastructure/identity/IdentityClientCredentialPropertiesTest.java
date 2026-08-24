package site.omagotchi.learningservice.team.infrastructure.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class IdentityClientCredentialPropertiesTest {

    private static final String VALID_USERNAME = "learning-service";
    private static final String VALID_PASSWORD =
            "test-only-learning-identity-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("유효한 Identity 클라이언트 설정 바인딩과 비밀번호 마스킹")
    void bindsValidPropertiesAndRedactsCredential() {
        // Given: 유효한 Identity 클라이언트 설정
        String usernameProperty = "clients.identity.username=" + VALID_USERNAME;
        String passwordProperty = "clients.identity.password=" + VALID_PASSWORD;

        // When: 설정 바인딩
        contextRunner
                .withPropertyValues(usernameProperty, passwordProperty)
                .run(context -> {
                    // Then: 설정값 반영과 비밀번호 마스킹
                    then(context).hasNotFailed();
                    IdentityClientCredentialProperties properties =
                            context.getBean(IdentityClientCredentialProperties.class);
                    String rendered = properties.toString();
                    assertSoftly(softly -> {
                        softly.assertThat(properties.username()).isEqualTo(VALID_USERNAME);
                        softly.assertThat(properties.password()).isEqualTo(VALID_PASSWORD);
                        softly.assertThat(rendered).contains("[REDACTED]");
                        softly.assertThat(rendered).doesNotContain(VALID_PASSWORD);
                    });
                });
    }

    @Test
    @DisplayName("Identity 클라이언트 설정 누락의 기동 실패")
    void rejectsMissingProperties() {
        // Given: Identity 클라이언트 설정 누락
        // When: 설정 바인딩
        contextRunner.run(context -> {
            // Then: 필수 설정 오류를 포함한 Application Context 기동 실패
            then(context.getStartupFailure())
                    .isNotNull()
                    .hasStackTraceContaining("clients.identity.username은 필수입니다.")
                    .hasStackTraceContaining("clients.identity.password는 필수입니다.");
        });
    }

    @Test
    @DisplayName("잘못된 Identity 클라이언트 비밀번호의 기동 실패 메시지 비노출")
    void doesNotExposeInvalidPassword() {
        // Given: 허용하지 않는 문자를 포함한 공유 Credential
        String invalidPassword = "a".repeat(31) + "+";

        // When: 설정 바인딩
        contextRunner
                .withPropertyValues(
                        "clients.identity.username=" + VALID_USERNAME,
                        "clients.identity.password=" + invalidPassword
                )
                .run(context -> {
                    // Then: 원문이 제거된 Application Context 기동 실패
                    Throwable failure = context.getStartupFailure();
                    then(failure).isNotNull();
                    then(stackTrace(failure)).doesNotContain(invalidPassword);
                });
    }

    @Test
    @DisplayName("Identity 클라이언트 설정 32자 최소 비밀번호 허용")
    void acceptsPasswordAtCharacterMinimum() {
        // Given: 최소 길이와 같은 32자 비밀번호
        String passwordAtCharacterMinimum = "a".repeat(32);

        // When: 설정 바인딩
        contextRunner
                .withPropertyValues(
                        "clients.identity.username=" + VALID_USERNAME,
                        "clients.identity.password=" + passwordAtCharacterMinimum
                )
                .run(context -> then(context).hasNotFailed());
    }

    @Test
    @DisplayName("Identity 클라이언트 설정 72자 최대 비밀번호 허용")
    void acceptsPasswordAtMaximumLength() {
        // Given: BCrypt 입력 한계와 같은 URL-safe ASCII 72자 비밀번호
        String passwordAtBcryptLimit = "a".repeat(72);

        // When: 설정 바인딩
        contextRunner
                .withPropertyValues(
                        "clients.identity.username=" + VALID_USERNAME,
                        "clients.identity.password=" + passwordAtBcryptLimit
                )
                .run(context -> then(context).hasNotFailed());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    @DisplayName("지원하지 않는 Identity 클라이언트 설정 거절")
    void rejectsInvalidSetting(
            String ignoredDescription,
            String username,
            String password,
            String expectedMessage
    ) {
        // Given: 지원하지 않는 Identity 클라이언트 설정
        String usernameProperty = "clients.identity.username=" + username;
        String passwordProperty = "clients.identity.password=" + password;

        // When: 설정 바인딩
        contextRunner
                .withPropertyValues(usernameProperty, passwordProperty)
                .run(context -> {
                    // Then: 설정 오류를 포함한 Application Context 기동 실패
                    then(context.getStartupFailure())
                            .isNotNull()
                            .hasStackTraceContaining(expectedMessage);
                });
    }

    private static Stream<Arguments> invalidSettings() {
        return Stream.of(
                Arguments.of(
                        "Basic Auth에 사용할 수 없는 username",
                        "learning:service",
                        VALID_PASSWORD,
                        "clients.identity.username에는 ':'를 사용할 수 없습니다."
                ),
                Arguments.of(
                        "빈 username",
                        "",
                        VALID_PASSWORD,
                        "clients.identity.username은 필수입니다."
                ),
                Arguments.of(
                        "빈 password",
                        VALID_USERNAME,
                        "",
                        "clients.identity.password는 필수입니다."
                ),
                Arguments.of(
                        "31자 password",
                        VALID_USERNAME,
                        "a".repeat(31),
                        "clients.identity.password는 32자 이상 72자 이하여야 합니다."
                ),
                Arguments.of(
                        "Unicode password",
                        VALID_USERNAME,
                        "가".repeat(32),
                        "clients.identity.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다."
                ),
                Arguments.of(
                        "Base64URL에 포함되지 않는 ASCII password",
                        VALID_USERNAME,
                        "a".repeat(31) + "+",
                        "clients.identity.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다."
                ),
                Arguments.of(
                        "72자를 넘는 password",
                        VALID_USERNAME,
                        "a".repeat(73),
                        "clients.identity.password는 32자 이상 72자 이하여야 합니다."
                )
        );
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityClientCredentialProperties.class)
    static class PropertiesConfig {
    }
}
