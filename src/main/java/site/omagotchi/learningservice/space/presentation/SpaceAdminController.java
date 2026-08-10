package site.omagotchi.learningservice.space.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.presentation.request.AssignLabCohortRequest;
import site.omagotchi.learningservice.space.presentation.request.CreateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.request.DeactivateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.request.UpdateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.response.CreateSpaceResponse;
import site.omagotchi.learningservice.space.presentation.response.SpaceStatusResponse;
import site.omagotchi.learningservice.space.presentation.response.UpdateSpaceResponse;
import site.omagotchi.learningservice.space.presentation.response.SpaceCohortResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/spaces")
public class SpaceAdminController {

    private final SpaceCommandService spaceCommandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSpaceResponse create(
            Authentication authentication,
            @Valid
            @RequestBody
            CreateSpaceRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CreateSpaceResponse.from(
                spaceCommandService.create(
                        request.toCommand(),
                        user.userId(),
                        user.globalRole()
                )
        );
    }

    @PutMapping("/{space-id}")
    public UpdateSpaceResponse update(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication,
            @Valid
            @RequestBody
            UpdateSpaceRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        UpdateSpaceCommand command =
                new UpdateSpaceCommand(
                        request.name(),
                        request.type(),
                        request.capacity()
                );

        return UpdateSpaceResponse.from(
                spaceCommandService.update(
                        spaceId,
                        command,
                        user.userId(),
                        user.globalRole()
                )
        );
    }

    @PatchMapping("/{space-id}/activate")
    public SpaceStatusResponse activate(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return SpaceStatusResponse.from(
                spaceCommandService.activate(
                        spaceId,
                        user.userId(),
                        user.globalRole()
                )
        );
    }

    @PatchMapping("/{space-id}/deactivate")
    public SpaceStatusResponse deactivate(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication,
            @Valid @RequestBody DeactivateSpaceRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return SpaceStatusResponse.from(
                spaceCommandService.deactivate(
                        spaceId,
                        request.inactiveReason(),
                        user.userId(),
                        user.globalRole()
                )
        );
    }

    @DeleteMapping("/{space-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        spaceCommandService.delete(
                spaceId,
                user.userId(),
                user.globalRole()
        );
    }

    @PutMapping("/{space-id}/cohort")
    public SpaceCohortResponse assignCohort(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication,
            @Valid @RequestBody AssignLabCohortRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return SpaceCohortResponse.from(
                spaceCommandService.assignCohort(
                        spaceId,
                        request.cohortId(),
                        user.userId(),
                        user.globalRole()
                )
        );
    }

    @DeleteMapping("/{space-id}/cohort")
    public SpaceCohortResponse unassignCohort(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return SpaceCohortResponse.from(
                spaceCommandService.unassignCohort(
                        spaceId,
                        user.userId(),
                        user.globalRole()
                )
        );
    }
}
