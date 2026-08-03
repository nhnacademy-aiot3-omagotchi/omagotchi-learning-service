package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum StudyRecordErrorCode implements ErrorCode {

    NOT_FOUND(
            ErrorType.NOT_FOUND,
            "STUDY_RECORD_NOT_FOUND",
            "공부 기록을 찾을 수 없습니다."
    ),
    OVERLAP(
            ErrorType.CONFLICT,
            "STUDY_RECORD_OVERLAP",
            "기존 공부 기록과 시간이 겹칩니다."
    ),
    AGGREGATION_BOUNDARY_CROSSED(
            ErrorType.INVALID_INPUT,
            "STUDY_RECORD_AGGREGATION_BOUNDARY_CROSSED",
            "공부 기록이 날짜 경계와 겹칩니다."
    ),
    ACTIVE_TIMER_CONFLICT(
            ErrorType.CONFLICT,
            "STUDY_RECORD_ACTIVE_TIMER_CONFLICT",
            "타이머가 실행 중일 때는 수동으로 공부 기록을 추가하거나 변경할 수 없습니다."
    ),
    VERSION_CONFLICT(
            ErrorType.CONFLICT,
            "STUDY_RECORD_VERSION_CONFLICT",
            "데이터가 최신이 아닙니다. 최신 데이터를 조회한 후 다시 요청해 주세요."
    ),
    WRITE_LOCK_TIMEOUT(
            ErrorType.CONFLICT,
            "STUDY_RECORD_WRITE_LOCK_TIMEOUT",
            "공부 기록 요청 타임아웃"
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
