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
        // HTTP Basic payload는 ASCII로 해석되므로(Prediction의 FastAPI HTTPBasic) username도 ASCII로 제한한다.
        // 비-ASCII를 허용하면 이 서비스의 기동 검증은 통과하고 Prediction 호출만 401로 실패해 원인 파악이 어렵다.
        // 0x21~0x7e(출력 가능한 ASCII)에서 구분자 ':'(0x3a)만 제외한다.
        @Pattern(
                regexp = "^[\\x21-\\x39\\x3b-\\x7e]*$",
                message = "clients.prediction.username은 공백을 제외한 ASCII 출력 가능 문자만 사용할 수 있습니다."
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
