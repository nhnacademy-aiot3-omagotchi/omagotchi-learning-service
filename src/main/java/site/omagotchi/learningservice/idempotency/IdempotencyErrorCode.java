package site.omagotchi.learningservice.idempotency;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum IdempotencyErrorCode implements ErrorCode {

    KEY_REUSED(
            ErrorType.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "동일한 멱등성 키가 다른 요청 데이터에 사용되었습니다."
    ),
    NOT_COMPLETED(
            ErrorType.CONFLICT,
            "IDEMPOTENCY_REQUEST_NOT_COMPLETED",
            "멱등성 요청이 아직 처리 중입니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;

    @Override
    public ErrorType type() { return type; }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }
}
