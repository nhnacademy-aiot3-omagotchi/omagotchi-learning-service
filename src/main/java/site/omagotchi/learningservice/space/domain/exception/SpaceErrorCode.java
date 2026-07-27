package site.omagotchi.learningservice.space.domain.exception;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum SpaceErrorCode implements ErrorCode {

    INVALID_NAME(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_NAME",
            "공간 이름이 올바르지 않습니다."
    ),
    INVALID_CAPACITY(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_CAPACITY",
            "공간 최대 인원이 올바르지 않습니다."
    ),
    DUPLICATE_NAME(
            ErrorType.CONFLICT,
            "SPACE_DUPLICATE_NAME",
            "이미 사용 중인 공간 이름입니다."
    ),
    NOT_FOUND(
            ErrorType.NOT_FOUND,
            "SPACE_NOT_FOUND",
            "공간을 찾을 수 없습니다."
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
