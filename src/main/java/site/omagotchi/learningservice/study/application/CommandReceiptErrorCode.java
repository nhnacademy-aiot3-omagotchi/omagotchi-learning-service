package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum CommandReceiptErrorCode implements ErrorCode {

    COMMAND_ID_CONFLICT(
            ErrorType.CONFLICT,
            "IDEMPOTENT_COMMAND_CONFLICT",
            "동일한 명령 ID로 다른 요청 내용이 전달되었습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
