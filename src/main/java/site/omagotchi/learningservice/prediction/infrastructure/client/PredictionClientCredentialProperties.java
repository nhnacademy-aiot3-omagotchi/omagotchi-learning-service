package site.omagotchi.learningservice.prediction.infrastructure.client;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

// 공부시간 예측 호출에만 사용하는 Credential
@Validated
@ConfigurationProperties(prefix = "clients.prediction")
public record PredictionClientCredentialProperties(

        @NotBlank(message = "clients.prediction.username은 필수입니다.")
        @Pattern(
                regexp = "^[^:]*$",
                message = "clients.prediction.username에는 ':'를 사용할 수 없습니다."
        )
        String username,

        @NotBlank(message = "clients.prediction.password는 필수입니다.")
        String password
) {

    @AssertTrue(message = "clients.prediction.password는 32자 이상 72자 이하여야 합니다.")
    public boolean isPasswordLengthValid() {
        return Objects.isNull(password) || password.length() >= 32 && password.length() <= 72;
    }

    @AssertTrue(message = "clients.prediction.password는 영문자·숫자·'-'·'_'만 사용할 수 있습니다.")
    public boolean isPasswordCharactersSetValid() {
        return Objects.isNull(password) || password.matches("[A-Za-z0-9_-]+");
    }

    @Override
    public String toString() {
        return "PredictionClientCredentialProperties[username=%s, password=[REDACTED]]".formatted(username);
    }
}
