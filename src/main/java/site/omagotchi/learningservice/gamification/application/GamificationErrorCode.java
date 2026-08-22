package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * 게이미피케이션 에러 코드
 */
@RequiredArgsConstructor
public enum GamificationErrorCode implements ErrorCode {

    REPRESENTATIVE_CHARACTER_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "REPRESENTATIVE_CHARACTER_NOT_FOUND",
            "대표 캐릭터를 찾을 수 없습니다."
    ),
    GAME_CHARACTER_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "GAME_CHARACTER_NOT_FOUND",
            "캐릭터를 찾을 수 없습니다."
    ),
    REPRESENTATIVE_CHARACTER_ALREADY_EXISTS(
            ErrorType.CONFLICT,
            "REPRESENTATIVE_CHARACTER_ALREADY_EXISTS",
            "이미 대표 캐릭터가 있습니다."
    ),
    INVALID_CHARACTER_NICKNAME(
            ErrorType.INVALID_INPUT,
            "INVALID_CHARACTER_NICKNAME",
            "닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있으며 금칙어를 포함할 수 없습니다."
    ),
    DUPLICATE_NICKNAME(
            ErrorType.CONFLICT,
            "DUPLICATE_NICKNAME",
            "이미 사용 중인 닉네임입니다."
    ),
    INVALID_CHARACTER_COLOR(
            ErrorType.INVALID_INPUT,
            "INVALID_CHARACTER_COLOR",
            "지원하지 않는 캐릭터 색상입니다."
    ),
    LEVEL_POLICY_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "LEVEL_POLICY_NOT_FOUND",
            "레벨 정책을 찾을 수 없습니다."
    ),
    DAILY_QUEST_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "DAILY_QUEST_NOT_FOUND",
            "일일 퀘스트를 찾을 수 없습니다."
    ),
    DAILY_QUEST_NOT_COMPLETED(
            ErrorType.CONFLICT,
            "DAILY_QUEST_NOT_COMPLETED",
            "완료되지 않은 퀘스트는 보상을 받을 수 없습니다."
    ),
    DAILY_QUEST_ALREADY_CLAIMED(
            ErrorType.CONFLICT,
            "DAILY_QUEST_ALREADY_CLAIMED",
            "이미 보상을 받은 퀘스트입니다."
    ),
    DAILY_QUEST_EXPIRED(
            ErrorType.CONFLICT,
            "DAILY_QUEST_EXPIRED",
            "지난 날짜의 퀘스트 보상은 받을 수 없습니다."
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
