package site.omagotchi.learningservice.weather.infrastructure.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KMA 연동 설정값 검증")
class KmaPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("baseUrl이 null/빈 문자열/공백이면 예외를 던진다")
    void throwsWhenBaseUrlIsBlank(String blankBaseUrl) {
        assertThatThrownBy(() ->
                new KmaProperties(blankBaseUrl, "service-key", Duration.ofSeconds(3))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kma.base-url");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("serviceKey가 null/빈 문자열/공백이면 예외를 던진다")
    void throwsWhenServiceKeyIsBlank(String blankServiceKey) {
        assertThatThrownBy(() ->
                new KmaProperties("http://example.com", blankServiceKey, Duration.ofSeconds(3))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kma.service-key");
    }

    @Test
    @DisplayName("requestTimeout이 null이면 기본값 3초로 대체된다")
    void fallsBackToDefaultTimeoutWhenNull() {
        KmaProperties properties = new KmaProperties("http://example.com", "service-key", null);

        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("requestTimeout이 0이면 기본값 3초로 대체된다")
    void fallsBackToDefaultTimeoutWhenZero() {
        KmaProperties properties = new KmaProperties("http://example.com", "service-key", Duration.ZERO);

        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("requestTimeout이 음수면 기본값 3초로 대체된다")
    void fallsBackToDefaultTimeoutWhenNegative() {
        KmaProperties properties = new KmaProperties("http://example.com", "service-key", Duration.ofSeconds(-1));

        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("requestTimeout이 양수면 그 값을 그대로 쓴다")
    void keepsPositiveTimeoutAsIs() {
        KmaProperties properties = new KmaProperties("http://example.com", "service-key", Duration.ofSeconds(5));

        assertThat(properties.requestTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}