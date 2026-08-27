package site.omagotchi.learningservice.weather.infrastructure.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("KMA 단기예보 조회와 응답 검증")
class KmaWeatherApiClientTest {

    private static final String BASE_URL = "http://kma-test";
    private static final String SERVICE_KEY = "test-service-key";

    // 2026-08-25 15:00 KST -> 14시 발표(14:10부터 조회 가능)를 쓰게 된다
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-25T06:00:00Z");

    private static final String EXPECTED_URL = BASE_URL
            + "/getVilageFcst?serviceKey=" + SERVICE_KEY
            + "&dataType=JSON&numOfRows=1000&pageNo=1"
            + "&base_date=20260825&base_time=1400&nx=60&ny=74";

    private MockRestServiceServer server;
    private KmaWeatherApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        this.server = MockRestServiceServer.bindTo(builder).build();

        KmaProperties properties = new KmaProperties(BASE_URL, SERVICE_KEY, Duration.ofSeconds(3));
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        this.client = new KmaWeatherApiClient(builder.build(), properties, clock);
    }

    @Test
    @DisplayName("정상 응답이면 예보를 조립해서 반환한다")
    void returnsForecastOnNormalResponse() {
        this.server.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(normalResponseBody(), MediaType.APPLICATION_JSON));

        List<WeatherForecast> result = this.client.getForecast(60, 74);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().temperatureCelsius()).isEqualTo(33);
        assertThat(result.getFirst().skyCondition()).isEqualTo(SkyCondition.CLOUDY);

        this.server.verify();
    }

    @Test
    @DisplayName("KMA가 오류 코드를 주면 예외를 던진다")
    void throwsWhenResultCodeIsNotNormal() {
        // 실제 KMA 응답 형태. 오류일 때는 body가 통째로 없다.
        String errorBody = """
                {
                  "response": {
                    "header": {
                      "resultCode": "10",
                      "resultMsg": "최근 3일 간의 자료만 제공합니다."
                    }
                  }
                }
                """;

        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(errorBody, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("정상 코드인데 body가 없으면 예외를 던진다")
    void throwsWhenBodyIsMissingDespiteNormalCode() {
        String body = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" }
                  }
                }
                """;

        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("정상 코드인데 예보 항목이 비어 있으면 예외를 던진다")
    void throwsWhenItemsAreEmptyDespiteNormalCode() {
        String body = """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
                    "body": {
                      "dataType": "JSON",
                      "items": { "item": [] },
                      "numOfRows": 0, "pageNo": 1, "totalCount": 0
                    }
                  }
                }
                """;

        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("header가 없는 응답이면 예외를 던진다")
    void throwsWhenHeaderIsMissing() {
        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess("{ \"response\": {} }", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 예외를 던진다")
    void throwsWhenResponseIsEmpty() {
        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("KMA에 연결하지 못하면 일시적 장애로 처리한다")
    void throwsServiceUnavailableOnNetworkFailure() {
        this.server.expect(requestTo(EXPECTED_URL))
                .andRespond(withException(new IOException("connection refused")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> this.client.getForecast(60, 74)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private static String normalResponseBody() {
        return """
                {
                  "response": {
                    "header": { "resultCode": "00", "resultMsg": "NORMAL_SERVICE" },
                    "body": {
                      "dataType": "JSON",
                      "items": {
                        "item": [
                          { "baseDate": "20260825", "baseTime": "1400", "category": "TMP",
                            "fcstDate": "20260825", "fcstTime": "1500", "fcstValue": "33", "nx": 60, "ny": 74 },
                          { "baseDate": "20260825", "baseTime": "1400", "category": "SKY",
                            "fcstDate": "20260825", "fcstTime": "1500", "fcstValue": "4", "nx": 60, "ny": 74 }
                        ]
                      },
                      "numOfRows": 2, "pageNo": 1, "totalCount": 2
                    }
                  }
                }
                """;
    }
}
