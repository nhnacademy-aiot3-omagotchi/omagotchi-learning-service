package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum EnvironmentErrorCode implements ErrorCode {

    INVALID_PAGE_REQUEST(
        ErrorType.INVALID_INPUT,
    "ENVIRONMENT_INVALID_PAGE_REQUEST",
    "페이지 요청값이 올바르지 않습니다."
    ),

    INVALID_PERIOD_REQUEST(
            ErrorType.INVALID_INPUT,
            "ENVIRONMENT_INVALID_PERIOD_REQUEST",
            "조회 기간이 올바르지 않습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String messag;

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
        return messag;
    }
}
