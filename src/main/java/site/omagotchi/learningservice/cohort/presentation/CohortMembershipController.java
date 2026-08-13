package site.omagotchi.learningservice.cohort.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ApproveMembershipRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.RejectMembershipRequest;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohort-memberships")
public class CohortMembershipController {

    private final CohortMembershipService membershipService;

    @PatchMapping("/{membershipId}/approve")
    public CohortMembershipResponse approve(
            @PathVariable Long membershipId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ApproveMembershipRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.approve(
                membershipId,
                request.toCommand(),
                user.userId(),
                user.globalRole()
        );
    }

    @PatchMapping("/{membershipId}/reject")
    public CohortMembershipResponse reject(
            @PathVariable Long membershipId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody RejectMembershipRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.reject(membershipId, request.toCommand(), user.userId());
    }
}
