package site.omagotchi.learningservice.user.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum UserProfileErrorCode implements ErrorCode {

    INVALID_NICKNAME(
            ErrorType.INVALID_INPUT,
            "USER_PROFILE_INVALID_NICKNAME",
            "닉네임은 2~12자여야 합니다."
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
