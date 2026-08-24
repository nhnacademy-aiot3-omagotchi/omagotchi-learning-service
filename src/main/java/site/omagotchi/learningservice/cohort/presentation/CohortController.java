package site.omagotchi.learningservice.cohort.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortAdminSummaryResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.result.CohortResponse;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.result.JoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.presentation.dto.request.AssignCohortManagerRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ChangeCohortMemberRoleRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ChangeCohortStatusRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.CreateCohortRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.CreateJoinRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.IssueJoinCodeRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.SaveAttendancePolicyRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.UpdateCohortRequest;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

/**
 * cohorts API
 * create, getCohorts, update, ChangeStatus
 */
@RestController
@RequestMapping("/api/v1/cohorts")
@RequiredArgsConstructor
public class CohortController {
    private final CohortService cohortService;
    private final JoinCodeService joinCodeService;
    private final CohortMembershipService membershipService;
    private final CohortManagerService managerService;
    private final CohortAttendancePolicyService attendancePolicyService;

    @PostMapping
    public CohortResponse create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateCohortRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return cohortService.create(request.toCommand(), user.userId(), user.globalRole());
    }

    @GetMapping
    public List<CohortResponse> getCohorts() {
        return cohortService.getCohorts();
    }

    @GetMapping("/admin-summary")
    public List<CohortAdminSummaryResponse> getAdminSummaries(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return cohortService.getAdminSummaries(user.globalRole());
    }

    @GetMapping("/{cohortId}")
    public CohortResponse getCohort(@PathVariable Long cohortId) {
        return cohortService.getCohort(cohortId);
    }

    @PatchMapping("/{cohortId}")
    public CohortResponse update(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateCohortRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return cohortService.update(cohortId, request.toCommand(), user.userId());
    }

    @PatchMapping("/{cohortId}/status")
    public CohortResponse changeStatus(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangeCohortStatusRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return cohortService.changeStatus(cohortId, request.toCommand(), user.globalRole());
    }

    @DeleteMapping("/{cohortId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        cohortService.delete(cohortId, user.globalRole());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cohortId}/join-code")
    public JoinCodeResponse getJoinCode(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return joinCodeService.getActiveJoinCode(cohortId, user.userId());
    }

    @PostMapping("/{cohortId}/join-code")
    public IssuedJoinCodeResponse issueJoinCode(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody IssueJoinCodeRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return joinCodeService.issue(cohortId, request.toCommand(), user.userId());
    }

    @PatchMapping("/{cohortId}/join-code/revoke")
    public JoinCodeResponse revokeJoinCode(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return joinCodeService.revoke(cohortId, user.userId());
    }

    @PostMapping("/join-requests")
    public CohortMembershipResponse join(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateJoinRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.join(request.toCommand(), user.userId());
    }

    @PostMapping("/applications")
    public CohortMembershipResponse apply(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateJoinRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.join(request.toCommand(), user.userId());
    }

    @GetMapping("/join-requests/me")
    public List<CohortMembershipResponse> getMyJoinRequests(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.getMyMemberships(user.userId());
    }

    @GetMapping("/{cohortId}/join-requests")
    public List<CohortMembershipResponse> getJoinRequests(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.getPendingJoinRequests(cohortId, user.userId());
    }

    @GetMapping("/{cohortId}/members")
    public List<CohortMembershipResponse> getMembers(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return membershipService.getMembers(cohortId, user.userId());
    }

    @PostMapping("/{cohortId}/managers")
    public CohortMembershipResponse assignManager(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody AssignCohortManagerRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return managerService.assignManager(
                cohortId,
                request.toCommand(),
                user.userId(),
                user.globalRole()
        );
    }

    @PatchMapping("/{cohortId}/members/{memberUserId}/role")
    public CohortMembershipResponse changeMemberRole(
            @PathVariable Long cohortId,
            @PathVariable UUID memberUserId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangeCohortMemberRoleRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return managerService.changeMemberRole(
                cohortId,
                memberUserId,
                request.toCommand(),
                user.userId(),
                user.globalRole()
        );
    }

    @GetMapping("/{cohortId}/attendance-policy")
    public CohortAttendancePolicyResponse getAttendancePolicy(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return attendancePolicyService.getPolicy(cohortId, user.userId());
    }

    @PutMapping("/{cohortId}/attendance-policy")
    public CohortAttendancePolicyResponse saveAttendancePolicy(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication,
            @Valid @RequestBody SaveAttendancePolicyRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return attendancePolicyService.savePolicy(cohortId, request.toCommand(), user.userId());
    }

}
