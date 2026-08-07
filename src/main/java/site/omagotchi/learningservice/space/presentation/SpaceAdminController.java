package site.omagotchi.learningservice.space.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.GlobalRole;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/spaces")
public class SpaceAdminController {

    private final SpaceCommandService spaceCommandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSpaceResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole,
            @Valid
            @RequestBody
            CreateSpaceRequest request
    ) {
        return CreateSpaceResponse.from(
                spaceCommandService.create(
                        request.toCommand(),
                        userId,
                        globalRole
                )
        );
    }

    @PutMapping("/{space-id}")
    public UpdateSpaceResponse update(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole,
            @Valid
            @RequestBody
            UpdateSpaceRequest request
    ) {
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
                        userId,
                        globalRole
                )
        );
    }

    @PatchMapping("/{space-id}/activate")
    public SpaceStatusResponse activate(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole
    ) {
        return SpaceStatusResponse.from(
                spaceCommandService.activate(spaceId, userId, globalRole)
        );
    }

    @PatchMapping("/{space-id}/deactivate")
    public SpaceStatusResponse deactivate(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole,
            @Valid @RequestBody DeactivateSpaceRequest request
    ) {
        return SpaceStatusResponse.from(
                spaceCommandService.deactivate(
                        spaceId,
                        request.inactiveReason(),
                        userId,
                        globalRole
                )
        );
    }

    @DeleteMapping("/{space-id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole
    ) {
        spaceCommandService.delete(spaceId, userId, globalRole);
    }

    @PutMapping("/{space-id}/cohort")
    public SpaceCohortResponse assignCohort(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole,
            @Valid @RequestBody AssignLabCohortRequest request
    ) {
        return SpaceCohortResponse.from(
                spaceCommandService.assignCohort(
                        spaceId,
                        request.cohortId(),
                        userId,
                        globalRole
                )
        );
    }

    @DeleteMapping("/{space-id}/cohort")
    public SpaceCohortResponse unassignCohort(
            @PathVariable("space-id") Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            GlobalRole globalRole
    ) {
        return SpaceCohortResponse.from(
                spaceCommandService.unassignCohort(
                        spaceId,
                        userId,
                        globalRole
                )
        );
    }
}
