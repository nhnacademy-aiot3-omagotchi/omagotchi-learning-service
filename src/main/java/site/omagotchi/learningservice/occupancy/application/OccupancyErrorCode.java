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

    // 403
    NOT_PRESENT(ErrorType.AUTHORIZATION, "OCCUPANCY_NOT_PRESENT",
            "출석(재실) 상태에서만 회의실을 점유할 수 있습니다."),                    // MR-22

    // 404
    SPACE_NOT_FOUND(ErrorType.NOT_FOUND, "OCCUPANCY_SPACE_NOT_FOUND",
            "공간을 찾을 수 없습니다."),

    // 409
    ROOM_ALREADY_OCCUPIED(ErrorType.CONFLICT, "OCCUPANCY_ROOM_ALREADY_OCCUPIED",
            "이미 사용 중인 회의실입니다. 공실 알림을 신청할 수 있습니다."),          // MR-08, MR-09
    ALREADY_OCCUPYING(ErrorType.CONFLICT, "OCCUPANCY_ALREADY_OCCUPYING",
            "이미 점유 중인 회의실이 있습니다."),                                    // MR-10
    ALREADY_PARTICIPATING(ErrorType.CONFLICT, "OCCUPANCY_ALREADY_PARTICIPATING",
            "이미 다른 회의에 참여 중입니다.");                               // MR-30

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
