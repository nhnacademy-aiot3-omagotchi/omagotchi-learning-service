package site.omagotchi.learningservice.space.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.presentation.response.SpacePresenceDetailResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/cohorts/{cohort-id}/spaces")
public class SpaceAdminPresenceController {

    private final SpaceQueryService spaceQueryService;

    @GetMapping("/{space-id}/presences")
    public SpacePresenceDetailResponse getCurrentPresences(
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        return SpacePresenceDetailResponse.from(
                spaceQueryService.getCurrentPresences(
                        cohortId,
                        spaceId,
                        AuthenticatedUser.from(authentication).userId()
                )
        );
    }
}
