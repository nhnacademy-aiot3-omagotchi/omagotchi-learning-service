package site.omagotchi.learningservice.prediction.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum PredictionErrorCode implements ErrorCode {

    PREDICTION_QUEST_DATA_INCONSISTENT(
            ErrorType.CONFLICT,
            "PREDICTION_QUEST_DATA_INCONSISTENT",
            "일일 퀘스트 데이터가 예측 피처 정책과 일치하지 않습니다."
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
