package site.omagotchi.learningservice.sensor.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.sensor.application.SensorSeriesService;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.presentation.response.SpaceSeriesResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/sensors")
public class SensorSeriesController {

    private final SensorSeriesService sensorSeriesService;

    @GetMapping("/space-series")
    public SpaceSeriesResponse getSpaceSeries(
            @PathVariable Long cohortId,
            @RequestParam @NotBlank
            @Pattern(regexp = "^[가-힣A-Za-z0-9 _-]{1,32}$", message = "location 형식이 올바르지 않습니다.") String location,
            @RequestParam @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "measurement 형식이 올바르지 않습니다.") String measurement,
            @RequestParam @NotBlank String window,
            JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        SpaceSeries series = sensorSeriesService.getSpaceSeries(
                cohortId,
                user.userId(),
                location,
                measurement,
                window
        );

        return SpaceSeriesResponse.from(series);
    }
}
