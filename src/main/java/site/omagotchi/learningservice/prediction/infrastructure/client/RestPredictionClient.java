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
import java.util.concurrent.TimeUnit;
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
        try {
            validateRequest(request);
        } catch (ConstraintViolationException | IllegalArgumentException exception) {
            int violationCount = exception instanceof ConstraintViolationException validationException
                    ? validationException.getConstraintViolations().size()
                    : 0;

            // 내부에서 조립한 prediction 요청의 계약 위반이다.
            // 요청 DTO와 위반 값은 개인정보·운영 데이터 노출을 막기 위해 기록하지 않는다.
            log.error(
                    "prediction-service 요청 계약 위반: "
                            + "위반수(violationCount)={}건, 예외유형(exception)={}",
                    violationCount,
                    exception.getClass().getName()
            );
            throw exception;
        }

        long startedAtNanos = System.nanoTime();

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

            int responseStatus = responseEntity.getStatusCode().value();

            if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                throw badResponse(
                        responseStatus,
                        BadResponseType.NON_SUCCESS_STATUS,
                        null,
                        startedAtNanos
                );
            }

            PredictionServiceResponse response = responseEntity.getBody();
            if (response == null) {
                // 빈 성공 본문은 기능별 필드 해석 이전에 판별 가능한 HTTP 계약 위반이다.
                throw badResponse(
                        responseStatus,
                        BadResponseType.EMPTY_BODY,
                        null,
                        startedAtNanos
                );
            }

            StudyTimePredictionResult result = validateResponse(
                    response,
                    responseStatus,
                    startedAtNanos
            );

            log.info(
                    "prediction-service 공부시간 예측 호출 성공: "
                            + "응답상태(status)={}, "
                            + "prediction-service 소요시간(downstreamElapsedMs)={}ms",
                    responseStatus,
                    elapsedMillis(startedAtNanos)
            );
            return result;
        } catch (RestClientResponseException exception) {
            // learning-service가 만든 내부 요청의 4xx도 외부 사용자 오류로 전달하지 않는다.
            throw badResponse(
                    exception.getStatusCode().value(),
                    BadResponseType.NON_SUCCESS_STATUS,
                    exception,
                    startedAtNanos
            );
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                log.warn(
                        "prediction-service 공부시간 예측 호출 시간 초과: "
                                + "실패사유(reason)=시간 초과(TIMEOUT), "
                                + "prediction-service 소요시간(downstreamElapsedMs)={}ms, "
                                + "예외유형(exception)={}",
                        elapsedMillis(startedAtNanos),
                        exception.getClass().getName()
                );
                throw PredictionClientException.timeout(exception);
            }

            log.error(
                    "prediction-service 공부시간 예측 호출 실패: "
                            + "실패사유(reason)=서비스 연결 불가(UNAVAILABLE), "
                            + "prediction-service 소요시간(downstreamElapsedMs)={}ms, "
                            + "예외유형(exception)={}",
                    elapsedMillis(startedAtNanos),
                    exception.getClass().getName()
            );
            throw PredictionClientException.unavailable(exception);
        } catch (RestClientException exception) {
            // JSON 역직렬화·지원하지 않는 Content-Type 등은 응답 의미가 아니라 프로토콜 실패다.
            throw badResponse(
                    null,
                    BadResponseType.UNREADABLE_BODY,
                    exception,
                    startedAtNanos
            );
        }
    }

    private StudyTimePredictionResult validateResponse(
            PredictionServiceResponse response,
            int responseStatus,
            long startedAtNanos
    ) {
        Double predictedStudyHours = response.predictedStudyHours();
        if (predictedStudyHours == null) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.MISSING_PREDICTED_STUDY_HOURS,
                    null,
                    startedAtNanos
            );
        }
        if (!Double.isFinite(predictedStudyHours)) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.NON_FINITE_PREDICTED_STUDY_HOURS,
                    null,
                    startedAtNanos
            );
        }
        if (predictedStudyHours < 0.0
                || predictedStudyHours > MAX_PREDICTED_STUDY_HOURS) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.OUT_OF_RANGE_PREDICTED_STUDY_HOURS,
                    null,
                    startedAtNanos
            );
        }
        if (!StringUtils.hasText(response.modelVersion())) {
            throw badResponse(
                    responseStatus,
                    BadResponseType.MISSING_MODEL_VERSION,
                    null,
                    startedAtNanos
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
            Throwable cause,
            long startedAtNanos
    ) {
        String status = responseStatus == null
                ? "알 수 없음(unknown)"
                : responseStatus.toString();
        String causeType = cause == null
                ? "없음(none)"
                : cause.getClass().getName();
        // 응답 본문은 기록하지 않고 상태와 실패 유형만 남긴다.
        log.error(
                "prediction-service 응답 계약 위반: "
                        + "응답상태(status)={}, 실패상세(failure)={}({}), "
                        + "prediction-service 소요시간(downstreamElapsedMs)={}ms, "
                        + "예외유형(exception)={}",
                status,
                badResponseDescription(responseError),
                responseError,
                elapsedMillis(startedAtNanos),
                causeType
        );
        return PredictionClientException.badResponse(responseStatus, cause);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
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

    private String badResponseDescription(BadResponseType responseError) {
        return switch (responseError) {
            case NON_SUCCESS_STATUS -> "성공이 아닌 HTTP 상태";
            case EMPTY_BODY -> "응답 본문 없음";
            case UNREADABLE_BODY -> "응답 본문 해석 불가";
            case MISSING_PREDICTED_STUDY_HOURS -> "예측시간 누락";
            case NON_FINITE_PREDICTED_STUDY_HOURS -> "유한하지 않은 예측시간";
            case OUT_OF_RANGE_PREDICTED_STUDY_HOURS -> "범위를 벗어난 예측시간";
            case MISSING_MODEL_VERSION -> "모델 버전 누락";
        };
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
