package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("예측 서비스 RestClient 설정")
class PredictionRestClientConfigTest {

    private static final String BASE_URL = "http://prediction-service:8080";
    private static final String PREDICTION_PATH = "/api/v1/predictions/study-time";
    private static final String USERNAME = "learning-service";
    private static final String PASSWORD = "test-only-learning-prediction-password";

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        PredictionClientProperties properties = new PredictionClientProperties(
                BASE_URL,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
        PredictionClientCredentialProperties credentials =
                new PredictionClientCredentialProperties(USERNAME, PASSWORD);

        RestClient configured = new PredictionRestClientConfig()
                .predictionRestClient(RestClient.builder(), properties, credentials);

        // 설정이 지정한 requestFactory를 목으로 교체하기 위해 mutate()로 빌더를 되찾는다
        // mutate()는 기본 헤더를 보존하므로 Basic 인증 적용 여부를 그대로 검증할 수 있다
        RestClient.Builder mutated = configured.mutate();
        server = MockRestServiceServer.bindTo(mutated).build();
        client = mutated.build();
    }

    // 헤더 테스트
    @Test
    @DisplayName("예측 요청에 관계 전용 Credential의 Basic 인증 헤더 포함")
    void sendsBasicAuthorizationHeader() {
        // given: 관계 전용 Credential로 만든 예상 Authorization 헤더
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(
                (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + PREDICTION_PATH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedHeader))
                .andRespond(withSuccess());

        // when: 예측 요청 전송
        client.post().uri(PREDICTION_PATH).retrieve().toBodilessEntity();

        // then: 기대한 헤더로 요청이 나갔는지 확인
        server.verify();
    }
}