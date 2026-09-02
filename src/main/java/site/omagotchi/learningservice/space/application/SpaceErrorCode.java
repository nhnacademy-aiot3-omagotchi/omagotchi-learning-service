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
    INVALID_SPACE_ID(
            ErrorType.INVALID_INPUT,
            "SPACE_INVALID_SPACE_ID",
            "공간 ID가 올바르지 않습니다."
    ),
    COHORT_ID_REQUIRED(
            ErrorType.INVALID_INPUT,
            "SPACE_COHORT_ID_REQUIRED",
            "관리하는 활성 기수가 여러 개이면 기수 ID를 지정해야 합니다."
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
    SPACE_HAS_CURRENT_PRESENCE(
            ErrorType.CONFLICT,
            "SPACE_HAS_CURRENT_PRESENCE",
            "현재 체류 중인 사용자가 있어 공간을 변경할 수 없습니다."
    ),
    SPACE_HAS_RETURN_RESERVATION(
            ErrorType.CONFLICT,
            "SPACE_HAS_RETURN_RESERVATION",
            "회의 종료 후 복귀할 사용자가 있어 공간을 변경할 수 없습니다."
    ),
    LAB_NOT_SELECTABLE(
            ErrorType.CONFLICT,
            "LAB_NOT_SELECTABLE",
            "자기 기수의 활성 실습실만 선택할 수 있습니다."
    ),
    LAB_CAPACITY_EXCEEDED(
            ErrorType.CONFLICT,
            "LAB_CAPACITY_EXCEEDED",
            "실습실 정원이 가득 찼습니다."
    ),
    STUDY_SPACE_NOT_SELECTABLE(
            ErrorType.CONFLICT,
            "STUDY_SPACE_NOT_SELECTABLE",
            "활성 공용 학습 공간만 선택할 수 있습니다."
    ),
    LAST_ACTIVE_LAB_REQUIRED(
            ErrorType.CONFLICT,
            "LAST_ACTIVE_LAB_REQUIRED",
            "활성 기수에는 활성 실습실이 최소 1개 필요합니다."
    ),
    SPACE_STATE_CHANGED(
            ErrorType.CONFLICT,
            "SPACE_STATE_CHANGED",
            "공간 상태가 동시에 변경되었습니다. 다시 시도해 주세요."
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
    // 센서가 남은 채 삭제하면 그 센서는 어느 기수에서도 보이지 않고, 기본키가 EUI라
    // 재등록도 막힌다. 삭제를 막아 미아가 생기는 것 자체를 예방한다.
    SPACE_HAS_SENSOR_DELETE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "SPACE_HAS_SENSOR_DELETE_NOT_ALLOWED",
            "센서가 배치된 공간은 삭제할 수 없습니다. 센서를 다른 공간으로 옮긴 뒤 삭제하세요."
    ),
    // 관리 주체가 없다는 것은 "이 요청자에게 권한이 없다"가 아니라 "누구에게도 없다"는 뜻이라
    // 상태가 아닌 권한 문제다 — 명세 01 §5 "관리 주체 없는 공간 삭제 → 403, 비활성화로 대체 안내".
    UNMANAGED_SPACE_DELETE_NOT_ALLOWED(
            ErrorType.AUTHORIZATION,
            "SPACE_UNMANAGED_DELETE_NOT_ALLOWED",
            "관리 주체가 없는 공간은 삭제할 수 없습니다. 비활성화를 이용하세요."
    ),
    SPACE_ALREADY_ASSIGNED(
            ErrorType.CONFLICT,
            "SPACE_ALREADY_ASSIGNED",
            "이미 기수에 배정된 공간입니다."
    ),
    SPACE_NOT_ASSIGNED(
            ErrorType.CONFLICT,
            "SPACE_NOT_ASSIGNED",
            "기수에 배정되지 않은 공간입니다."
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
