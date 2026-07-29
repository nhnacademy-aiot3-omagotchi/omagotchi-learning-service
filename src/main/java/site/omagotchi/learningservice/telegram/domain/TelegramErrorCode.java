package site.omagotchi.learningservice.telegram.domain;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum TelegramErrorCode implements ErrorCode {

    TELEGRAM_LINK_TOKEN_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "TELEGRAM_LINK_TOKEN_NOT_FOUND",
            "Telegram 연결 토큰을 찾을 수 없습니다."
    ),
    TELEGRAM_LINK_TOKEN_EXPIRED(
            ErrorType.CONFLICT,
            "TELEGRAM_LINK_TOKEN_EXPIRED",
            "만료되었거나 이미 사용된 Telegram 연결 토큰입니다."
    ),
    TELEGRAM_WEBHOOK_UNSUPPORTED(
            ErrorType.INVALID_INPUT,
            "TELEGRAM_WEBHOOK_UNSUPPORTED",
            "지원하지 않는 Telegram Webhook 요청입니다."
    ),
    TELEGRAM_USER_LINK_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "TELEGRAM_USER_LINK_NOT_FOUND",
            "Telegram 연동 정보를 찾을 수 없습니다."
    ),
    TELEGRAM_CHAT_ALREADY_LINKED(
            ErrorType.CONFLICT,
            "TELEGRAM_CHAT_ALREADY_LINKED",
            "이미 다른 사용자와 연결된 Telegram 채팅입니다."
    ),
    ATTENDANCE_REMINDER_MEMBERSHIP_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ATTENDANCE_REMINDER_MEMBERSHIP_NOT_FOUND",
            "출결 알림 대상 기수 소속을 찾을 수 없습니다."
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
