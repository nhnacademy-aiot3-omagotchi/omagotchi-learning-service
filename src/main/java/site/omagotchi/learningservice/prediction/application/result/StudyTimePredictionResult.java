package site.omagotchi.learningservice.prediction.application.result;

public record StudyTimePredictionResult(
        Double predictedStudyHours,
        String modelVersion
) {
}
