package site.omagotchi.learningservice.prediction.presentation.response;

import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

public record StudyTimePredictionResponse(
        Double predictedStudyHours,
        String modelVersion
) {

    public static StudyTimePredictionResponse from(StudyTimePredictionResult result) {
        return new StudyTimePredictionResponse(
                result.predictedStudyHours(),
                result.modelVersion()
        );
    }
}
