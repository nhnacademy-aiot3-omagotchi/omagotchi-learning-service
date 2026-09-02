package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * CohortErrorCode
 */
@RequiredArgsConstructor
public enum CohortErrorCode implements ErrorCode {

    // 400 Bad Request
    INVALID_COHORT_PERIOD(
            ErrorType.INVALID_INPUT,
            "COHORT_INVALID_PERIOD",
            "기수 시작일은 종료일보다 늦을 수 없습니다."
    ),
    INVALID_COHORT_STATUS_TRANSITION(
            ErrorType.INVALID_INPUT,
            "COHORT_INVALID_STATUS_TRANSITION",
            "허용되지 않은 기수 상태 변경입니다."
    ),
    INVALID_MEMBERSHIP_STATUS_TRANSITION(
            ErrorType.INVALID_INPUT,
            "MEMBERSHIP_INVALID_STATUS_TRANSITION",
            "허용되지 않은 기수 소속 상태 변경입니다."
    ),
    REJECTION_REASON_REQUIRED(
            ErrorType.INVALID_INPUT,
            "MEMBERSHIP_REJECTION_REASON_REQUIRED",
            "거절 사유는 필수입니다."
    ),
    JOIN_CODE_REQUIRED(
            ErrorType.INVALID_INPUT,
            "JOIN_CODE_REQUIRED",
            "가입 코드가 필요합니다."
    ),
    JOIN_CODE_EXPIRES_AT_INVALID(
            ErrorType.INVALID_INPUT,
            "JOIN_CODE_EXPIRES_AT_INVALID",
            "가입 코드 만료 시각은 현재보다 이후여야 합니다."
    ),

    // 403 Forbidden
    COHORT_ACCESS_DENIED(
            ErrorType.AUTHORIZATION,
            "COHORT_ACCESS_DENIED",
            "해당 기수에 접근할 권한이 없습니다."
    ),
    COHORT_MANAGER_REQUIRED(
            ErrorType.AUTHORIZATION,
            "COHORT_MANAGER_REQUIRED",
            "기수 관리자 권한이 필요합니다."
    ),
    SYSTEM_ADMIN_REQUIRED(
            ErrorType.AUTHORIZATION,
            "SYSTEM_ADMIN_REQUIRED",
            "시스템 관리자 권한이 필요합니다."
    ),

    // 404 Not Found
    COHORT_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "COHORT_NOT_FOUND",
            "기수를 찾을 수 없습니다."
    ),
    /*
     * 기수는 있으나 출결 정책 행이 없는 상태다.
     *
     * 기수 자체가 없는 COHORT_NOT_FOUND와 반드시 구분해야 한다. 화면은 이 코드를 받으면
     * "기수 없음"이 아니라 "정책 미설정"으로 처리하고 기본값 입력 폼을 연다.
     *
     * code 문자열은 AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND와 의도적으로 같다.
     * 정책 행은 cohort 소유이고 attendance가 빌려 쓰는 구조라 패키지 순환을 피하려고
     * 양쪽에 선언을 두되, Browser가 보는 계약은 하나로 유지한다.
     */
    ATTENDANCE_POLICY_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ATTENDANCE_POLICY_NOT_FOUND",
            "출결 정책이 설정되지 않았습니다."
    ),
    COHORT_MEMBERSHIP_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "MEMBERSHIP_NOT_FOUND",
            "기수 소속을 찾을 수 없습니다."
    ),
    JOIN_CODE_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "JOIN_CODE_NOT_FOUND",
            "가입 코드를 찾을 수 없습니다."
    ),

    // 409 Conflict
    COHORT_ALREADY_CLOSED(
            ErrorType.CONFLICT,
            "COHORT_ALREADY_CLOSED",
            "이미 종료된 기수입니다."
    ),
    COHORT_ACTIVE_MANAGER_REQUIRED(
            ErrorType.CONFLICT,
            "COHORT_ACTIVE_MANAGER_REQUIRED",
            "운영 전환을 위해 활성 관리자 소속이 필요합니다."
    ),
    COHORT_ACTIVE_LAB_REQUIRED(
            ErrorType.CONFLICT,
            "ACTIVE_LAB_REQUIRED",
            "운영 전환을 위해 활성 실습실이 최소 1개 필요합니다."
    ),
    COHORT_MANAGER_PERIOD_CONFLICT(
            ErrorType.CONFLICT,
            "COHORT_MANAGER_PERIOD_CONFLICT",
            "동일한 운영 기간에 여러 기수의 관리자로 배치할 수 없습니다."
    ),
    COHORT_DELETE_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "COHORT_DELETE_NOT_ALLOWED",
            "준비 상태의 기수만 삭제할 수 있습니다."
    ),
    COHORT_DELETE_CONFLICT(
            ErrorType.CONFLICT,
            "COHORT_DELETE_CONFLICT",
            "연결된 운영 데이터가 있어 기수를 삭제할 수 없습니다."
    ),
    COHORT_MEMBERSHIP_DUPLICATED(
            ErrorType.CONFLICT,
            "MEMBERSHIP_DUPLICATED",
            "이미 대기 중이거나 활성 상태인 기수 소속이 있습니다."
    ),
    JOIN_CODE_EXPIRED(
            ErrorType.CONFLICT,
            "JOIN_CODE_EXPIRED",
            "만료된 가입 코드입니다."
    ),
    JOIN_CODE_REVOKED(
            ErrorType.CONFLICT,
            "JOIN_CODE_REVOKED",
            "폐기된 가입 코드입니다."
    ),
    JOIN_CODE_ALREADY_EXISTS(
            ErrorType.CONFLICT,
            "JOIN_CODE_ALREADY_EXISTS",
            "이미 유효한 가입 코드가 있습니다."
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
