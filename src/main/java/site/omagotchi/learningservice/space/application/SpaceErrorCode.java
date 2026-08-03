package site.omagotchi.learningservice.space.application;

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
    INVALID_COHORT_ID(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_COHORT_ID",
            "공간 관리 주체 기수 ID가 올바르지 않습니다."
    ),
    COHORT_ID_REQUIRED(
            ErrorType.INVALID_INPUT,
            "SPACE_COHORT_ID_REQUIRED",
            "관리하는 활성 기수가 여러 개이면 기수 ID를 지정해야 합니다."
    ),
    LAB_ONLY_COHORT_ASSIGNMENT(
            ErrorType.INVALID_INPUT,
            "SPACE_LAB_ONLY_COHORT_ASSIGNMENT",
            "실습실에만 기수를 배정하거나 해제할 수 있습니다."
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
    COHORT_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "COHORT_NOT_FOUND",
            "기수를 찾을 수 없습니다."
    ),
    ACCESS_DENIED(
            ErrorType.AUTHORIZATION,
            "SPACE_ACCESS_DENIED",
            "해당 공간을 관리할 권한이 없습니다."
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
    UNMANAGED_SPACE_COMMAND_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_UNMANAGED_COMMAND_NOT_ALLOWED",
            "관리 주체 기수가 없는 공간은 변경할 수 없습니다."
    ),
    ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED",
            "기수에 배정된 실습실은 공간 유형을 변경할 수 없습니다."
    ),
    LAB_ALREADY_ASSIGNED(
            ErrorType.CONFLICT,
            "SPACE_LAB_ALREADY_ASSIGNED",
            "이미 기수에 배정된 실습실입니다."
    ),
    LAB_NOT_ASSIGNED(
            ErrorType.CONFLICT,
            "SPACE_LAB_NOT_ASSIGNED",
            "기수에 배정되지 않은 실습실입니다."
    ),
    ACTIVE_COHORT_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ACTIVE_COHORT_NOT_FOUND",
            "요청자가 관리하는 활성 기수를 찾을 수 없습니다."
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
