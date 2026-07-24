package site.omagotchi.learningservice.study.domain.exception;

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
