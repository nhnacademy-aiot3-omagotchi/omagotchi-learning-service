package site.omagotchi.learningservice.team.infrastructure.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Identity 계정 조회 호출에만 사용하는 Learning 프로세스 Credential
@Validated
@ConfigurationProperties(prefix = "clients.identity")
public record IdentityClientCredentialProperties(
        @NotBlank(message = "clients.identity.username은 필수입니다.")
        @Pattern(
                regexp = "^[^:]*$",
                message = "clients.identity.username에는 ':'를 사용할 수 없습니다."
        )
        String username,

        @NotBlank(message = "clients.identity.password는 필수입니다.")
        String password
) {

    @AssertTrue(message = "clients.identity.password는 32자 이상 72자 이하여야 합니다.")
    public boolean isPasswordLengthValid() {
        return password == null || password.length() >= 32 && password.length() <= 72;
    }

    @AssertTrue(message = "clients.identity.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다.")
    public boolean isPasswordCharacterSetValid() {
        return password == null || password.matches("[A-Za-z0-9_-]+");
    }

    @Override
    public String toString() {
        return "IdentityClientCredentialProperties[username=" + username
                + ", password=[REDACTED]]";
    }
}
