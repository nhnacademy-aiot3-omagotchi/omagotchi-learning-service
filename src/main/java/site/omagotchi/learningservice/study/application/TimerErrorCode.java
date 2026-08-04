package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum TimerErrorCode implements ErrorCode {

    ALREADY_RUNNING(
            ErrorType.CONFLICT,
            "TIMER_ALREADY_RUNNING",
            "이미 실행 중인 타이머가 존재합니다."
    ),
    RUN_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "TIMER_RUN_NOT_FOUND",
            "타이머 실행을 찾을 수 없습니다."
    ),
    ALREADY_ENDED(
            ErrorType.CONFLICT,
            "TIMER_ALREADY_ENDED",
            "이미 종료된 타이머 실행입니다."
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
