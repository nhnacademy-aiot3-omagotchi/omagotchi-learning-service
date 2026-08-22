package site.omagotchi.learningservice.user.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum UserProfileErrorCode implements ErrorCode {

    INVALID_NICKNAME(
            ErrorType.INVALID_INPUT,
            "USER_PROFILE_INVALID_NICKNAME",
            "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있으며 금칙어를 포함할 수 없습니다."
    ),
    DUPLICATE_NICKNAME(
            ErrorType.CONFLICT,
            "USER_PROFILE_DUPLICATE_NICKNAME",
            "이미 사용 중인 닉네임입니다."
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
