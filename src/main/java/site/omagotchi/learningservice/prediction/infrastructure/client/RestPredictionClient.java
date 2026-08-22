package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

@Component
public class RestPredictionClient implements PredictionClient {

    private static final String STUDY_TIME_PREDICTION_PATH = "/api/v1/predictions/study-time";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final RestClient restClient;

    public RestPredictionClient(
            @Qualifier("predictionRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Override
    public StudyTimePredictionResult predict(
            StudyTimePredictionRequest request,
            String requestId
    ) {
        RestClient.RequestBodySpec requestSpec = restClient.post()
                .uri(STUDY_TIME_PREDICTION_PATH);

        if (StringUtils.hasText(requestId)) {
            requestSpec.header(REQUEST_ID_HEADER, requestId);
        }

        PredictionServiceResponse response = requestSpec
                .body(request)
                .retrieve()
                .body(PredictionServiceResponse.class);

        if (response == null) {
            return null;
        }

        return new StudyTimePredictionResult(
                response.predictedStudyHours(),
                response.modelVersion()
        );
    }

    private record PredictionServiceResponse(
            Double predictedStudyHours,
            String modelVersion
    ) {
    }
}
