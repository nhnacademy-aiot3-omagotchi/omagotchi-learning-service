package site.omagotchi.learningservice.cohort.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ApproveMembershipRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.RejectMembershipRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cohort-memberships")
public class CohortMembershipController {

    private final CohortMembershipService membershipService;

    @PatchMapping("/{membershipId}/approve")
    public CohortMembershipResponse approve(
            @PathVariable Long membershipId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody ApproveMembershipRequest request
    ) {
        return membershipService.approve(membershipId, request.toCommand(), userId, globalRole);
    }

    @PatchMapping("/{membershipId}/reject")
    public CohortMembershipResponse reject(
            @PathVariable Long membershipId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody RejectMembershipRequest request
    ) {
        return membershipService.reject(membershipId, request.toCommand(), userId);
    }
}
