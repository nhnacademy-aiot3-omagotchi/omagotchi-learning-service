package site.omagotchi.learningservice.prediction.infrastructure.client;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException.Reason;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("예측 서비스 HTTP 호출")
@ExtendWith(OutputCaptureExtension.class)
class RestPredictionClientTest {

    private static final String BASE_URL = "http://prediction-service";
    private static final String PREDICTION_URL =
            BASE_URL + "/api/v1/predictions/study-time";
    private static final String REQUEST_ID = "prediction-request-id";

    private ValidatorFactory validatorFactory;
    private Validator validator;
    private MockRestServiceServer server;
    private RestPredictionClient client;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestPredictionClient(builder.build(), validator);
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("유효한 요청과 Request ID 전달 후 정상 응답 반환")
    void returnsResponseAfterSendingValidRequest(CapturedOutput output) {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Request-ID", REQUEST_ID))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.studyLag1").value(0.0))
                .andExpect(jsonPath("$.tomorrowDayOfWeekConsistent").doesNotExist())
                .andRespond(withSuccess(
                        "{\"predictedStudyHours\":7.21,\"modelVersion\":\"study-time-model\"}",
                        MediaType.APPLICATION_JSON
                ));

        // When
        StudyTimePredictionResult result = client.predict(validRequest(0.0), REQUEST_ID);

        // Then
        assertEquals(7.21, result.predictedStudyHours());
        assertEquals("study-time-model", result.modelVersion());
        assertThat(output.getOut())
                .contains("prediction-service 공부시간 예측 호출에 성공했습니다.")
                .contains("status=200")
                .contains("elapsedMs=")
                .contains("modelVersion=study-time-model");
        server.verify();
    }

    @Test
    @DisplayName("요청 DTO 계약 위반 시 송신 전 예외")
    void rejectsInvalidRequestBeforeSending(CapturedOutput output) {
        // Given
        StudyTimePredictionRequest invalidRequest = validRequest(12.0);

        // When
        ConstraintViolationException exception = assertThrows(
                ConstraintViolationException.class,
                () -> client.predict(invalidRequest, REQUEST_ID)
        );

        // Then
        assertEquals(1, exception.getConstraintViolations().size());
        assertThat(output.getOut())
                .contains("prediction-service 요청 계약을 위반했습니다.")
                .contains("violationCount=1")
                .contains("exception=jakarta.validation.ConstraintViolationException");
        server.verify();
    }

    @Test
    @DisplayName("요일 조합 계약 위반 시 송신 전 예외")
    void rejectsInconsistentCalendarBeforeSending() {
        // Given
        StudyTimePredictionRequest invalidRequest = requestWithInconsistentCalendar();

        // When
        ConstraintViolationException exception = assertThrows(
                ConstraintViolationException.class,
                () -> client.predict(invalidRequest, REQUEST_ID)
        );

        // Then
        assertEquals(1, exception.getConstraintViolations().size());
        server.verify();
    }

    @Test
    @DisplayName("null 요청 시 호출 계약 위반 예외")
    void rejectsNullRequestBeforeSending(CapturedOutput output) {
        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.predict(null, REQUEST_ID)
        );

        // Then
        assertEquals("예측 요청은 null일 수 없습니다.", exception.getMessage());
        assertThat(output.getOut())
                .contains("prediction-service 요청 계약을 위반했습니다.")
                .contains("violationCount=0")
                .contains("exception=java.lang.IllegalArgumentException");
        server.verify();
    }

    @Test
    @DisplayName("downstream 400 응답을 잘못된 의존 서비스 응답으로 변환")
    void convertsDownstreamClientError() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"COMMON_INVALID_REQUEST\"}"));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(400, exception.getResponseStatus());
        assertInstanceOf(RestClientResponseException.class, exception.getCause());
        server.verify();
    }

    @Test
    @DisplayName("downstream 500 응답을 잘못된 의존 서비스 응답으로 변환")
    void convertsDownstreamServerError() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(500, exception.getResponseStatus());
        assertInstanceOf(RestClientResponseException.class, exception.getCause());
        server.verify();
    }

    @Test
    @DisplayName("downstream 3xx 응답을 잘못된 의존 서비스 응답으로 변환")
    void convertsDownstreamRedirection() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"predictedStudyHours\":7.21,"
                                + "\"modelVersion\":\"study-time-model\"}"));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(302, exception.getResponseStatus());
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    @DisplayName("빈 성공 본문을 잘못된 의존 서비스 응답으로 변환")
    void convertsEmptyResponse() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(204, exception.getResponseStatus());
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    @DisplayName("범위를 벗어난 성공 응답을 기록하고 잘못된 의존 서비스 응답으로 변환")
    void convertsAndLogsOutOfRangeSuccessResponse(CapturedOutput output) {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withSuccess(
                        "{\"predictedStudyHours\":11.6,"
                                + "\"modelVersion\":\"study-time-model\"}",
                        MediaType.APPLICATION_JSON
                ));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(200, exception.getResponseStatus());
        assertThat(output.getOut())
                .contains("prediction-service 응답이 올바르지 않습니다.")
                .contains("status=200")
                .contains("failure=OUT_OF_RANGE_PREDICTED_STUDY_HOURS")
                .contains("elapsedMs=")
                .contains("exception=none");
        server.verify();
    }

    @Test
    @DisplayName("모델 버전이 없는 성공 응답을 잘못된 의존 서비스 응답으로 변환")
    void convertsSuccessResponseWithoutModelVersion() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withSuccess(
                        "{\"predictedStudyHours\":7.21,\"modelVersion\":\" \"}",
                        MediaType.APPLICATION_JSON
                ));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertEquals(200, exception.getResponseStatus());
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 JSON을 잘못된 의존 서비스 응답으로 변환")
    void convertsMalformedResponse() {
        // Given
        server.expect(requestTo(PREDICTION_URL))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> client.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.BAD_RESPONSE, exception.getReason());
        assertNull(exception.getResponseStatus());
        assertInstanceOf(RestClientException.class, exception.getCause());
        server.verify();
    }

    @Test
    @DisplayName("연결 실패를 의존 서비스 사용 불가로 변환")
    void convertsConnectionFailure(CapturedOutput output) {
        // Given
        RestPredictionClient failingClient = clientThrowing(
                new ConnectException("connection refused")
        );

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> failingClient.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.UNAVAILABLE, exception.getReason());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(ConnectException.class, exception.getCause().getCause());
        assertThat(output.getOut())
                .contains("prediction-service 공부시간 예측 호출에 실패했습니다.")
                .contains("reason=UNAVAILABLE")
                .contains("elapsedMs=")
                .contains("exception=org.springframework.web.client.ResourceAccessException")
                .doesNotContain("connection refused");
    }

    @Test
    @DisplayName("응답 시간 초과를 의존 서비스 timeout으로 변환")
    void convertsTimeout(CapturedOutput output) {
        // Given
        RestPredictionClient failingClient = clientThrowing(
                new SocketTimeoutException("read timed out")
        );

        // When
        PredictionClientException exception = assertThrows(
                PredictionClientException.class,
                () -> failingClient.predict(validRequest(0.0), REQUEST_ID)
        );

        // Then
        assertEquals(Reason.TIMEOUT, exception.getReason());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(SocketTimeoutException.class, exception.getCause().getCause());
        assertThat(output.getOut())
                .contains("prediction-service 공부시간 예측 호출 시간이 초과되었습니다.")
                .contains("elapsedMs=")
                .contains("exception=org.springframework.web.client.ResourceAccessException")
                .doesNotContain("read timed out");
    }

    private RestPredictionClient clientThrowing(java.io.IOException failure) {
        RestClient failingRestClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, method) -> {
                    throw failure;
                })
                .build();
        return new RestPredictionClient(failingRestClient, validator);
    }

    private StudyTimePredictionRequest validRequest(double studyLag1) {
        return new StudyTimePredictionRequest(
                studyLag1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0,
                null, 0.0, 0.0, 0.0,
                null, null,
                1, 0L, 0L, 0.0,
                1, 0, 0, 0, 0, 0, 0,
                0L
        );
    }

    private StudyTimePredictionRequest requestWithInconsistentCalendar() {
        return new StudyTimePredictionRequest(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0,
                null, 0.0, 0.0, 0.0,
                null, null,
                1, 0L, 0L, 0.0,
                1, 0, 0, 0, 0, 1, 0,
                0L
        );
    }
}
