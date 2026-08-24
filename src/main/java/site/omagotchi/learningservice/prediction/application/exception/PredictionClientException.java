package site.omagotchi.learningservice.prediction.application.exception;

import lombok.Getter;

import java.util.Objects;

/**
 * prediction-service 호출 과정에서 발생한 전송 또는 프로토콜 실패.
 *
 * <p>prediction 모듈 내부에서 실패 원인을 보존하기 위한 예외다. 비정상 응답은 내부 장애로
 * 기록하고 외부에는 기존 공통 계약의 일반 500 응답만 공개한다. downstream 응답 상세는 공개하지 않는다.
 */
@Getter
public final class PredictionClientException extends RuntimeException {

    private final Reason reason;
    private final Integer responseStatus;

    private PredictionClientException(
            Reason reason,
            Integer responseStatus,
            Throwable cause
    ) {
        super(buildMessage(reason, responseStatus), cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.responseStatus = responseStatus;
    }

    public static PredictionClientException badResponse(
            Integer responseStatus,
            Throwable cause
    ) {
        return new PredictionClientException(
                Reason.BAD_RESPONSE,
                responseStatus,
                cause
        );
    }

    public static PredictionClientException unavailable(Throwable cause) {
        return new PredictionClientException(
                Reason.UNAVAILABLE,
                null,
                Objects.requireNonNull(cause, "cause")
        );
    }

    public static PredictionClientException timeout(Throwable cause) {
        return new PredictionClientException(
                Reason.TIMEOUT,
                null,
                Objects.requireNonNull(cause, "cause")
        );
    }

    private static String buildMessage(Reason reason, Integer responseStatus) {
        String status = responseStatus == null ? "none" : responseStatus.toString();
        return "Prediction service call failed: reason="
                + Objects.requireNonNull(reason, "reason")
                + ", responseStatus=" + status;
    }

    public enum Reason {
        BAD_RESPONSE,
        UNAVAILABLE,
        TIMEOUT
    }
}
