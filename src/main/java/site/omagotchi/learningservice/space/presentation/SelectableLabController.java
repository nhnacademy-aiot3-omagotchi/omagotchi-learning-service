package site.omagotchi.learningservice.space.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.space.application.LabAccessQueryService;
import site.omagotchi.learningservice.space.presentation.response.SelectableLabResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/spaces/labs")
public class SelectableLabController {

    private final LabAccessQueryService labAccessQueryService;

    @GetMapping
    public List<SelectableLabResponse> findSelectableLabs(
            @PathVariable("cohort-id") Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return labAccessQueryService.findSelectableLabs(cohortId, user.userId())
                .stream()
                .map(SelectableLabResponse::from)
                .toList();
    }
}
