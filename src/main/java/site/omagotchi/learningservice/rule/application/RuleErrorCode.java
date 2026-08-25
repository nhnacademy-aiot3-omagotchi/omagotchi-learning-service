package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum RuleErrorCode implements ErrorCode {
    //룰
    RULE_INVALID_CONDITION(
            ErrorType.INVALID_INPUT,
            "RULE_INVALID_CONDITION",
            "룰 조건이 올바르지 않습니다."
    ),

    RULE_ALREADY_EXISTS(
            ErrorType.CONFLICT,
            "RULE_ALREADY_EXISTS",
            "이미 룰이 존재합니다."
    ),
    RULE_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "RULE_NOT_FOUND",
            "룰을 찾을 수 없습니다."
    ),


    //센서
    DEVICE_INVALID_ATTRIBUTE(
            ErrorType.INVALID_INPUT,
            "DEVICE_INVALID_ATTRIBUTE",
            "디바이스의 정보가 올바르지않습니다."
    ),

    DEVICE_ALREADY_EXISTS(
            ErrorType.CONFLICT,
            "DEVICE_ALREADY_EXISTS",
            "디바이스가 이미 존재합니다"
    ),

    DEVICE_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "DEVICE_NOT_FOUND",
            "디바이스를 찾을 수 없습니다."
    ),

    DEVICE_SPACE_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "DEVICE_SPACE_NOT_FOUND",
            "센서를 배치할 공간을 찾을 수 없습니다."
    ),

    SPACE_HAS_NO_DEVICE(
            ErrorType.NOT_FOUND,
            "SPACE_HAS_NO_DEVICE",
            "해당 공간에 등록된 센서가 없습니다."
    ),

    RULE_VERSION_CONFLICT(
            ErrorType.CONFLICT,
            "RULE_VERSION_CONFLICT",
            "룰이 이미 변경되었습니다. 최신 상태를 다시 조회해 주세요."
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
