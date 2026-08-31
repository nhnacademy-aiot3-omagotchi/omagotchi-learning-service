package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * 점유 기능의 에러 코드.
 *
 * <p>{@link ErrorType}이 곧 HTTP 상태다. 상태를 바꾸려면 여기 type만 고치면 되고
 * 컨트롤러는 손대지 않는다.</p>
 *
 * <p>409가 셋인 것은 점유의 배타 제약이 전부 부분 유니크 인덱스이기 때문이다.
 * 서비스의 select 선검사는 동시 요청을 막지 못하므로 같은 상황이 선검사에서도
 * 인덱스 위반에서도 발생한다. {@code OccupancyConstraintTranslator}가 후자를
 * 전자와 같은 코드로 변환해 클라이언트가 경로를 구분하지 않아도 되게 한다.</p>
 */
@RequiredArgsConstructor
public enum OccupancyErrorCode implements ErrorCode {

    // 400
    NOT_MEETING_ROOM(ErrorType.INVALID_INPUT, "OCCUPANCY_NOT_MEETING_ROOM",
            "회의실만 점유할 수 있습니다."),                                        // MR-20
    SPACE_INACTIVE(ErrorType.INVALID_INPUT, "OCCUPANCY_SPACE_INACTIVE",
            "현재 이용할 수 없는 공간입니다."),                                      // RM-13
    DIFFERENT_COHORT(ErrorType.INVALID_INPUT, "OCCUPANCY_DIFFERENT_COHORT",
            "같은 기수의 사용자만 참여자로 추가할 수 있습니다."),                     // MR-33
    OCCUPIER_CANNOT_LEAVE(ErrorType.INVALID_INPUT, "OCCUPANCY_OCCUPIER_CANNOT_LEAVE",
            "점유자는 반납으로만 회의를 종료할 수 있습니다."),                        // MR-31
    ALERT_ROOM_AVAILABLE(ErrorType.INVALID_INPUT, "OCCUPANCY_ALERT_ROOM_AVAILABLE",
            "이미 사용 가능한 회의실입니다."),                                       // MR-02
    ALERT_OCCUPIER_CANNOT_REQUEST(ErrorType.INVALID_INPUT, "OCCUPANCY_ALERT_OCCUPIER_CANNOT_REQUEST",
            "본인이 점유 중인 회의실에는 공실 알림을 신청할 수 없습니다."),           // MR-02
    ALERT_COHORT_ID_REQUIRED(ErrorType.INVALID_INPUT, "OCCUPANCY_ALERT_COHORT_ID_REQUIRED",
            "여러 기수에 소속되어 있습니다. 신청할 기수를 지정해 주세요."),           // 다기수 담당자

    // 403
    NOT_PRESENT(ErrorType.AUTHORIZATION, "OCCUPANCY_NOT_PRESENT",
            "출석(재실) 상태에서만 회의실을 점유할 수 있습니다."),                    // MR-22
    TARGET_NOT_PRESENT(ErrorType.AUTHORIZATION, "OCCUPANCY_TARGET_NOT_PRESENT",
            "재실 상태인 사용자만 참여자로 추가할 수 있습니다."),                     // MR-19
    NOT_OCCUPIER(ErrorType.AUTHORIZATION, "OCCUPANCY_NOT_OCCUPIER",
            "점유자만 참여자를 관리할 수 있습니다."),                                 // MR-29, MR-31
    PARTICIPANT_ACCESS_DENIED(ErrorType.AUTHORIZATION, "OCCUPANCY_PARTICIPANT_ACCESS_DENIED",
            "현재 회의 참여자만 참여자 목록을 볼 수 있습니다."),
    ALERT_COHORT_ACCESS_DENIED(ErrorType.AUTHORIZATION, "OCCUPANCY_ALERT_COHORT_ACCESS_DENIED",
            "해당 기수의 활성 소속이 아닙니다."),                                     // MR-02
    NOT_COHORT_MANAGER(ErrorType.AUTHORIZATION, "OCCUPANCY_NOT_COHORT_MANAGER",
            "점유자가 속한 기수의 매니저만 강제 종료할 수 있습니다."),               // MR-21

    // 404
    SPACE_NOT_FOUND(ErrorType.NOT_FOUND, "OCCUPANCY_SPACE_NOT_FOUND",
            "공간을 찾을 수 없습니다."),
    PARTICIPANT_NOT_FOUND(ErrorType.NOT_FOUND, "OCCUPANCY_PARTICIPANT_NOT_FOUND",
            "참여자를 찾을 수 없습니다."),
    ALERT_NOT_FOUND(ErrorType.NOT_FOUND, "OCCUPANCY_ALERT_NOT_FOUND",
            "공실 알림 신청을 찾을 수 없습니다."),                                   // MR-17

    // 409
    ROOM_ALREADY_OCCUPIED(ErrorType.CONFLICT, "OCCUPANCY_ROOM_ALREADY_OCCUPIED",
            "이미 사용 중인 회의실입니다. 공실 알림을 신청할 수 있습니다."),          // MR-08, MR-09
    ALREADY_OCCUPYING(ErrorType.CONFLICT, "OCCUPANCY_ALREADY_OCCUPYING",
            "이미 점유 중인 회의실이 있습니다."),                                    // MR-10
    ALREADY_PARTICIPATING(ErrorType.CONFLICT, "OCCUPANCY_ALREADY_PARTICIPATING",
            "이미 다른 회의에 참여 중입니다."),                                // MR-30
    OCCUPANCY_ENDED(ErrorType.CONFLICT, "OCCUPANCY_ENDED",
            "이미 종료된 점유입니다."),
    CAPACITY_EXCEEDED(ErrorType.CONFLICT, "OCCUPANCY_CAPACITY_EXCEEDED",
            "회의실 정원을 초과했습니다."),                                    // MR-28
    OCCUPIER_MEMBERSHIP_INACTIVE(ErrorType.CONFLICT, "OCCUPANCY_OCCUPIER_MEMBERSHIP_INACTIVE",
            "점유자의 기수 소속이 유효하지 않습니다."),  // MR-33·MR-21, 요청자가 아니라 점유자 쪽 원인
    EXTENSION_TOO_EARLY(ErrorType.CONFLICT, "OCCUPANCY_EXTENSION_TOO_EARLY",
            "만료 30분 전부터 연장할 수 있습니다."),                            // MR-06
    EXTENSION_LIMIT_EXCEEDED(ErrorType.CONFLICT, "OCCUPANCY_EXTENSION_LIMIT_EXCEEDED",
            "연장은 최대 2회까지 가능합니다."),                                 // MR-06
    ALERT_ALREADY_REQUESTED(ErrorType.CONFLICT, "OCCUPANCY_ALERT_ALREADY_REQUESTED",
            "이미 신청한 공실 알림이 있습니다.");                               // MR-15

    // 만료 후 연장과 종료된 점유 반납에는 전용 코드를 두지 않고 OCCUPANCY_ENDED를 재사용한다.
    // "이미 종료된 점유입니다"가 두 상황 모두에서 사용자에게 정확한 설명이고,
    // 클라이언트가 구분해 분기할 이유가 없다. 스케줄러가 아직 EXPIRED로 바꾸지 않아
    // status는 ACTIVE인 창에서도 expires_at이 지났으면 같은 코드로 거부한다.

    // 재실 조회 자체의 실패(명세서 02, 503)에는 전용 코드를 두지 않는다.
    // BusinessException은 ErrorType.INTERNAL을 전달할 수 없고(의도된 가드), 이 실패는
    // 클라이언트가 분기할 외부 계약도 없다 — 04-error-handling의 "예상하지 못한 실패"로
    // 분류해 원본 예외를 그대로 전파시키고 GlobalExceptionHandler의 마지막 경계에서
    // COMMON_INTERNAL_SERVER_ERROR(500)로 변환한다 (RoomOccupancyService 참고).

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
