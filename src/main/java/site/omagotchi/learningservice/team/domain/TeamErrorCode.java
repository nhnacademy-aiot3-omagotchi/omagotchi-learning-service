package site.omagotchi.learningservice.team.domain;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * 팀 도메인 에러 코드.
 *
 * <p>{@link ErrorType}이 곧 HTTP 상태다 — {@code ErrorHttpStatusMapper}가
 * INVALID_INPUT→400, AUTHORIZATION→403, NOT_FOUND→404, CONFLICT→409로 옮긴다.
 * 따라서 상태 코드를 바꾸려면 여기 type을 고치면 되고, 컨트롤러는 손댈 필요가 없다.</p>
 *
 * <p>409가 많은 것은 팀 도메인의 제약 대부분이 부분 유니크 인덱스이기 때문이다.
 * 서비스의 select 선검사는 동시 요청을 막지 못하므로, 같은 상황이 선검사에서도
 * 인덱스 위반에서도 발생할 수 있다. {@code TeamConstraintTranslator}가 후자를
 * 전자와 같은 코드로 변환해 클라이언트가 경로를 구분하지 않아도 되게 한다.</p>
 */
@RequiredArgsConstructor
public enum TeamErrorCode implements ErrorCode {

    // 400
    INVALID_NAME(ErrorType.INVALID_INPUT, "TEAM_INVALID_NAME",
            "팀 이름은 공백을 제외하고 1~30자여야 합니다."),
    COHORT_REQUIRED(ErrorType.INVALID_INPUT, "TEAM_COHORT_REQUIRED",
            "대상 기수를 지정하세요."),
    TARGET_NOT_IN_COHORT(ErrorType.INVALID_INPUT, "TEAM_TARGET_NOT_IN_COHORT",
            "팀과 같은 기수에 소속된 사용자만 추가할 수 있습니다."),
    MASTER_CANNOT_BE_KICKED(ErrorType.INVALID_INPUT, "TEAM_MASTER_CANNOT_BE_KICKED",
            "팀 마스터는 제외할 수 없습니다. 탈퇴를 이용하세요."),
    CANNOT_DELEGATE_TO_SELF(ErrorType.INVALID_INPUT, "TEAM_CANNOT_DELEGATE_TO_SELF",
            "자기 자신에게는 위임할 수 없습니다."),

    // 403
    COHORT_ACCESS_DENIED(ErrorType.AUTHORIZATION, "TEAM_COHORT_ACCESS_DENIED",
            "해당 기수에 소속되어 있지 않습니다."),
    MASTER_REQUIRED(ErrorType.AUTHORIZATION, "TEAM_MASTER_REQUIRED",
            "팀 마스터만 수행할 수 있습니다."),
    NOT_A_MEMBER(ErrorType.AUTHORIZATION, "TEAM_NOT_A_MEMBER",
            "해당 팀의 팀원이 아닙니다."),

    // 404
    TEAM_NOT_FOUND(ErrorType.NOT_FOUND, "TEAM_NOT_FOUND",
            "팀을 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(ErrorType.NOT_FOUND, "TEAM_MEMBER_NOT_FOUND",
            "팀원을 찾을 수 없습니다."),
    ACCOUNT_NOT_FOUND(ErrorType.NOT_FOUND, "TEAM_ACCOUNT_NOT_FOUND",
            "존재하지 않는 사용자입니다."),

    // 409
    DUPLICATE_NAME(ErrorType.CONFLICT, "TEAM_DUPLICATE_NAME",
            "같은 기수에 이미 사용 중인 팀 이름입니다."),
    ALREADY_IN_TEAM(ErrorType.CONFLICT, "TEAM_ALREADY_IN_TEAM",
            "이미 해당 기수의 팀에 소속되어 있습니다."),
    CAPACITY_EXCEEDED(ErrorType.CONFLICT, "TEAM_CAPACITY_EXCEEDED",
            "팀 최대 인원은 8명입니다."),
    ACCOUNT_WITHDRAWN(ErrorType.CONFLICT, "TEAM_ACCOUNT_WITHDRAWN",
            "탈퇴한 사용자는 추가할 수 없습니다."),
    DELEGATION_REQUIRED(ErrorType.CONFLICT, "TEAM_DELEGATION_REQUIRED",
            "팀원이 남아 있어 위임 후에만 탈퇴할 수 있습니다."),
    MASTER_STATE_CONFLICT(ErrorType.CONFLICT, "TEAM_MASTER_STATE_CONFLICT",
            "마스터 상태가 변경되었습니다. 다시 시도하세요.");

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