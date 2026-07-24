package site.omagotchi.learningservice.cohort.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortStatusRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortMemberRoleRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortResponse;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.dto.command.AssignCohortManagerRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateCohortRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateJoinRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.IssueJoinCodeRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.dto.result.JoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.dto.command.UpdateCohortRequest;

import java.util.List;
import java.util.UUID;

/**
 * cohorts API
 * create, getCohorts, update, ChangeStatus
 */
@RestController
@RequestMapping("/api/cohorts")
@RequiredArgsConstructor
public class CohortController {
    private final CohortService cohortService;
    private final JoinCodeService joinCodeService;
    private final CohortMembershipService membershipService;
    private final CohortManagerService managerService;

    @PostMapping
    public CohortResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody CreateCohortRequest request
    ) {
        return cohortService.create(request, userId, globalRole);
    }

    @GetMapping
    public List<CohortResponse> getCohorts() {
        return cohortService.getCohorts();
    }

    @GetMapping("/{cohortId}")
    public CohortResponse getCohort(@PathVariable Long cohortId) {
        return cohortService.getCohort(cohortId);
    }

    @PatchMapping("/{cohortId}")
    public CohortResponse update(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateCohortRequest request
    ) {
        return cohortService.update(cohortId, request, userId);
    }

    @PatchMapping("/{cohortId}/status")
    public CohortResponse changeStatus(
            @PathVariable Long cohortId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody ChangeCohortStatusRequest request
    ) {
        return cohortService.changeStatus(cohortId, request, globalRole);
    }

    @GetMapping("/{cohortId}/join-code")
    public JoinCodeResponse getJoinCode(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return joinCodeService.getActiveJoinCode(cohortId, userId);
    }

    @PostMapping("/{cohortId}/join-code")
    public IssuedJoinCodeResponse issueJoinCode(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody IssueJoinCodeRequest request
    ) {
        return joinCodeService.issue(cohortId, request, userId);
    }

    @PatchMapping("/{cohortId}/join-code/revoke")
    public JoinCodeResponse revokeJoinCode(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return joinCodeService.revoke(cohortId, userId);
    }

    @PostMapping("/join-requests")
    public CohortMembershipResponse join(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateJoinRequest request
    ) {
        return membershipService.join(request, userId);
    }

    @GetMapping("/join-requests/me")
    public List<CohortMembershipResponse> getMyJoinRequests(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getMyMemberships(userId);
    }

    @GetMapping("/{cohortId}/join-requests")
    public List<CohortMembershipResponse> getJoinRequests(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getPendingJoinRequests(cohortId, userId);
    }

    @GetMapping("/{cohortId}/members")
    public List<CohortMembershipResponse> getMembers(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getMembers(cohortId, userId);
    }

    @PostMapping("/{cohortId}/managers")
    public CohortMembershipResponse assignManager(
            @PathVariable Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody AssignCohortManagerRequest request
    ) {
        return managerService.assignManager(cohortId, request, userId, globalRole);
    }

    @PatchMapping("/{cohortId}/members/{memberUserId}/role")
    public CohortMembershipResponse changeMemberRole(
            @PathVariable Long cohortId,
            @PathVariable UUID memberUserId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody ChangeCohortMemberRoleRequest request
    ) {
        return managerService.changeMemberRole(cohortId, memberUserId, request, userId, globalRole);
    }
}
