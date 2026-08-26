package site.omagotchi.learningservice.prediction.application.port;

import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

public interface PredictionClient {

    StudyTimePredictionResult predict(
            StudyTimePredictionRequest request,
            String requestId
    );
}
