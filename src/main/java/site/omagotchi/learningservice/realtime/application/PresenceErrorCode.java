package site.omagotchi.learningservice.realtime.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * Presence 세션 조작의 업무 오류.
 *
 * <p>STOMP 경로에서는 sessionId를 프레임워크가 채워 주지만 REST 경로에서는 요청자가 값을
 * 실어 보낸다. 따라서 소유자 대조 실패와 식별자 누락을 명시적인 업무 오류로 표현한다.
 *
 * <p>AccessDeniedException을 쓰면 GlobalExceptionHandler의 catch-all
 * {@code @ExceptionHandler(Exception.class)}에 먼저 걸려 403이 아니라 500으로 응답된다.
 * 이 저장소의 다른 도메인과 동일하게 BusinessException + ErrorCode 규약을 따른다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PresenceErrorCode implements ErrorCode {

    SESSION_ID_REQUIRED(
            ErrorType.INVALID_INPUT,
            "PRESENCE_SESSION_ID_REQUIRED",
            "Presence 세션 식별자가 필요합니다."
    ),
    SESSION_ACCESS_DENIED(
            ErrorType.AUTHORIZATION,
            "PRESENCE_SESSION_ACCESS_DENIED",
            "다른 사용자의 Presence 세션을 조작할 수 없습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;
}
