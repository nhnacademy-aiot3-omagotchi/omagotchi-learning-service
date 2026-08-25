package site.omagotchi.learningservice.prediction.infrastructure.client;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class RestPredictionClient implements PredictionClient {

    private static final String STUDY_TIME_PREDICTION_PATH = "/api/v1/predictions/study-time";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final double MAX_PREDICTED_STUDY_HOURS = 11.5;

    private final RestClient restClient;
    private final Validator validator;

    public RestPredictionClient(
            @Qualifier("predictionRestClient") RestClient restClient,
            Validator validator
    ) {
        this.restClient = restClient;
        this.validator = validator;
    }

    @Override
    public StudyTimePredictionResult predict(
            StudyTimePredictionRequest request,
            String requestId
    ) {
        validateRequest(request);

        try {
            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri(STUDY_TIME_PREDICTION_PATH);

            if (StringUtils.hasText(requestId)) {
                requestSpec.header(REQUEST_ID_HEADER, requestId);
            }

            ResponseEntity<PredictionServiceResponse> responseEntity = requestSpec
                    .body(request)
                    .retrieve()
                    .toEntity(PredictionServiceResponse.class);
            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                throw badResponse(
                        responseEntity.getStatusCode().value(),
                        BadResponseType.NON_SUCCESS_STATUS,
                        null
                );
            }
            PredictionServiceResponse response = responseEntity.getBody();

            if (response == null) {
                // 빈 성공 본문은 기능별 필드 해석 이전에 판별 가능한 HTTP 계약 위반이다.
                throw badResponse(
                        responseEntity.getStatusCode().value(),
                        BadResponseType.EMPTY_BODY,
                        null
                );
            }

            return validateResponse(
                    response,
                    responseEntity.getStatusCode().value()
            );
        } catch (RestClientResponseException exception) {
            // learning-service가 만든 내부 요청의 4xx도 외부 사용자 오류로 전달하지 않는다.
            throw badResponse(
                    exception.getStatusCode().value(),
                    BadResponseType.NON_SUCCESS_STATUS,
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw PredictionClientException.timeout(exception);
            }
            throw PredictionClientException.unavailable(exception);
        } catch (RestClientException exception) {
            // JSON 역직렬화·지원하지 않는 Content-Type 등은 응답 의미가 아니라 프로토콜 실패다.
            throw badResponse(
                    null,
                    BadResponseType.UNREADABLE_BODY,
                    exception
            );
        }
    }

    private StudyTimePredictionResult validateResponse(
            PredictionServiceResponse response,
            int responseStatus
    ) {
        Double predictedStudyHours = response.predictedStudyHours();
        if (predictedStudyHours == null) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.MISSING_PREDICTED_STUDY_HOURS,
                    null
            );
        }
        if (!Double.isFinite(predictedStudyHours)) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.NON_FINITE_PREDICTED_STUDY_HOURS,
                    null
            );
        }
        if (predictedStudyHours < 0.0
                || predictedStudyHours > MAX_PREDICTED_STUDY_HOURS) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.OUT_OF_RANGE_PREDICTED_STUDY_HOURS,
                    null
            );
        }
        if (!StringUtils.hasText(response.modelVersion())) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.MISSING_MODEL_VERSION,
                    null
            );
        }
        return new StudyTimePredictionResult(
                predictedStudyHours,
                response.modelVersion()
        );
    }

    private PredictionClientException badResponse(
            Integer responseStatus,
            BadResponseType responseError,
            Throwable cause
    ) {
        String status = responseStatus == null ? "unknown" : responseStatus.toString();
        String causeType = cause == null ? "none" : cause.getClass().getName();
        // 구조화 응답 오류 로그에는 downstream 본문을 넣지 않고 상태와 실패 유형만 기록한다.
        log.error(
                "Prediction service response error: status={}, failure={}, cause={}",
                status,
                responseError,
                causeType
        );
        return PredictionClientException.badResponse(responseStatus, cause);
    }

    private void validateRequest(StudyTimePredictionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("예측 요청은 null일 수 없습니다.");
        }

        Set<ConstraintViolation<StudyTimePredictionRequest>> violations =
                validator.validate(request);
        if (!violations.isEmpty()) {
            // 외부 사용자의 입력이 아니라 내부에서 조립한 DTO의 계약 위반이므로 400으로 바꾸지 않는다.
            throw new ConstraintViolationException(
                    "prediction-service 요청 계약을 위반했습니다.",
                    violations
            );
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record PredictionServiceResponse(
            Double predictedStudyHours,
            String modelVersion
    ) {
    }

    private enum BadResponseType {
        NON_SUCCESS_STATUS,
        EMPTY_BODY,
        UNREADABLE_BODY,
        MISSING_PREDICTED_STUDY_HOURS,
        NON_FINITE_PREDICTED_STUDY_HOURS,
        OUT_OF_RANGE_PREDICTED_STUDY_HOURS,
        MISSING_MODEL_VERSION
    }
}
