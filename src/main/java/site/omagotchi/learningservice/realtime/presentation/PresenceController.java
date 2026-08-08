package site.omagotchi.learningservice.realtime.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.realtime.application.CohortPresenceSnapshot;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cohorts/me/presence")
public class PresenceController {

    private final CohortPresenceService presenceService;

    @GetMapping
    public CohortPresenceSnapshot getMyCohortPresence(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return presenceService.currentUserSnapshot(user.userId());
    }
}
