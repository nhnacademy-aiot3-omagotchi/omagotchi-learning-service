package site.omagotchi.learningservice.global.security.rule;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Rule의 임계치 기준 조회에만 사용하는 관계 전용 HTTP Basic Credential
@Validated
@ConfigurationProperties(prefix = "auth.rule")
public record RuleCredentialProperties(
        @NotBlank(message = "auth.rule.username은 비어 있을 수 없습니다.")
        @Pattern(
                regexp = "^[^:]*$",
                message = "auth.rule.username에는 ':'를 사용할 수 없습니다."
        )
        String username,

        @NotBlank(message = "auth.rule.password는 비어 있을 수 없습니다.")
        String password
) {

    @AssertTrue(message = "auth.rule.password는 32자 이상 72자 이하여야 합니다.")
    public boolean isPasswordLengthValid() {
        return password == null || password.length() >= 32 && password.length() <= 72;
    }

    @AssertTrue(message = "auth.rule.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다.")
    public boolean isPasswordCharacterSetValid() {
        return password == null || password.matches("[A-Za-z0-9_-]+");
    }

    @Override
    public String toString() {
        return "RuleCredentialProperties[username=" + username
                + ", password=[REDACTED]]";
    }
}
