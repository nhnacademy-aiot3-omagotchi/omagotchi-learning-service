package site.omagotchi.learningservice.cohort.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortResponse;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.dto.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.dto.result.JoinCodeResponse;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.presentation.dto.request.AssignCohortManagerRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ChangeCohortMemberRoleRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.ChangeCohortStatusRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.CreateCohortRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.CreateJoinRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.IssueJoinCodeRequest;
import site.omagotchi.learningservice.cohort.presentation.dto.request.UpdateCohortRequest;

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

    @PostMapping
    public CohortResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody CreateCohortRequest request
    ) {
        return cohortService.create(request.toCommand(), userId, globalRole);
    }

    @GetMapping
    public List<CohortResponse> getCohorts() {
        return cohortService.getCohorts();
    }

    @GetMapping("/{cohort-id}")
    public CohortResponse getCohort(@PathVariable("cohort-id") Long cohortId) {
        return cohortService.getCohort(cohortId);
    }

    @PatchMapping("/{cohort-id}")
    public ResponseEntity<Void> update(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateCohortRequest request
    ) {
        cohortService.update(cohortId, request.toCommand(), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{cohort-id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody ChangeCohortStatusRequest request
    ) {
        cohortService.changeStatus(cohortId, request.toCommand(), globalRole);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cohort-id}/join-codes")
    public JoinCodeResponse getJoinCode(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return joinCodeService.getActiveJoinCode(cohortId, userId);
    }

    @PostMapping("/{cohort-id}/join-codes")
    public IssuedJoinCodeResponse issueJoinCode(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody IssueJoinCodeRequest request
    ) {
        return joinCodeService.issue(cohortId, request.toCommand(), userId);
    }

    @PostMapping("/{cohort-id}/join-codes/revoke")
    public JoinCodeResponse revokeJoinCode(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return joinCodeService.revoke(cohortId, userId);
    }

    @PostMapping("/join-requests")
    public CohortMembershipResponse join(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateJoinRequest request
    ) {
        return membershipService.join(request.toCommand(), userId);
    }

    @GetMapping("/join-requests/me")
    public List<CohortMembershipResponse> getMyJoinRequests(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getMyMemberships(userId);
    }

    @GetMapping("/{cohort-id}/join-requests")
    public List<CohortMembershipResponse> getJoinRequests(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getPendingJoinRequests(cohortId, userId);
    }

    @GetMapping("/{cohort-id}/members")
    public List<CohortMembershipResponse> getMembers(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return membershipService.getMembers(cohortId, userId);
    }

    @PostMapping("/{cohort-id}/managers")
    public CohortMembershipResponse assignManager(
            @PathVariable("cohort-id") Long cohortId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody AssignCohortManagerRequest request
    ) {
        return managerService.assignManager(cohortId, request.toCommand(), userId, globalRole);
    }

    @PatchMapping("/{cohort-id}/members/{member-user-id}/role")
    public ResponseEntity<Void> changeMemberRole(
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("member-user-id") UUID memberUserId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER") String globalRole,
            @Valid @RequestBody ChangeCohortMemberRoleRequest request
    ) {
        managerService.changeMemberRole(cohortId, memberUserId, request.toCommand(), userId, globalRole);
        return ResponseEntity.noContent().build();
    }
}
