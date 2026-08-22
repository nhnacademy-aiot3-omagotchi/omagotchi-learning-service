package site.omagotchi.learningservice.prediction.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyTimePredictionService {

    public StudyTimePredictionResult predict(
            UUID userId,
            Long cohortId,
            String requestId
    ) {
        return null;
    }
}
