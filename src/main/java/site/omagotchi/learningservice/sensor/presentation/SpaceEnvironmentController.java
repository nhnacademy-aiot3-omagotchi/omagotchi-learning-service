package site.omagotchi.learningservice.sensor.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.sensor.application.SpaceEnvironmentService;
import site.omagotchi.learningservice.sensor.presentation.response.SpaceEnvironmentResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/sensors")
public class SpaceEnvironmentController {

    private final SpaceEnvironmentService spaceEnvironmentService;

    /** 임계값 룰과 달리 매니저 전용이 아니다. 시계열처럼 활성 기수원이면 볼 수 있다. */
    @GetMapping("/environment")
    public List<SpaceEnvironmentResponse> getEnvironments(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return spaceEnvironmentService.getCohortEnvironments(cohortId, user.userId()).stream()
                .map(SpaceEnvironmentResponse::from)
                .toList();
    }
}
