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
    INVALID_TYPE(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_TYPE",
            "공간 유형이 올바르지 않습니다."
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
    ),
    DELETED_SPACE(
            ErrorType.CONFLICT,
            "SPACE_ALREADY_DELETED",
            "삭제된 공간은 변경할 수 없습니다."
    ),
    ALREADY_ACTIVE(
            ErrorType.CONFLICT,
            "SPACE_ALREADY_ACTIVE",
            "이미 활성화된 공간입니다."
    ),
    ALREADY_INACTIVE(
            ErrorType.CONFLICT,
            "SPACE_ALREADY_INACTIVE",
            "이미 비활성화된 공간입니다."
    ),
    INVALID_INACTIVE_REASON(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_INACTIVE_REASON",
            "비활성 사유는 필수입니다."
    ),
    ACTIVE_OCCUPANCY_EXISTS(
            ErrorType.CONFLICT,
            "SPACE_ACTIVE_OCCUPANCY_EXISTS",
            "활성 점유가 존재하여 공간을 변경할 수 없습니다."
    ),
    ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED",
            "활성 공간의 정원은 축소할 수 없습니다."
    ),
    ACTIVE_TYPE_CHANGE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ACTIVE_TYPE_CHANGE_NOT_ALLOWED",
            "활성 공간의 유형은 변경할 수 없습니다."
    ),
    ACTIVE_SPACE_DELETE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ACTIVE_DELETE_NOT_ALLOWED",
            "활성 공간은 삭제할 수 없습니다."
    ),
    UNMANAGED_SPACE_DELETE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_UNMANAGED_DELETE_NOT_ALLOWED",
            "관리 주체가 없는 공간은 삭제할 수 없습니다."
    ),
    ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED",
            "기수에 배정된 실습실은 공간 유형을 변경할 수 없습니다."
    ),
    ASSIGNED_LAB_DELETE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ASSIGNED_LAB_DELETE_NOT_ALLOWED",
            "기수에 배정된 실습실은 삭제할 수 없습니다."
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
