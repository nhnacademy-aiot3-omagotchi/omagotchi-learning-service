package site.omagotchi.learningservice.prediction.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.presentation.response.StudyTimePredictionResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/predictions")
public class PredictionController {

    private final StudyTimePredictionService predictionService;

    @PostMapping("/study-time")
    public StudyTimePredictionResponse predictStudyTime(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return StudyTimePredictionResponse.from(
                predictionService.predict(user.userId(), cohortId, requestId)
        );
    }
}
