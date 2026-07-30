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
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.ActivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeactivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.AssignLabCohortUseCase;
import site.omagotchi.learningservice.space.application.port.in.UnassignLabCohortUseCase;
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

    private final CreateSpaceUseCase
            createSpaceUseCase;

    private final ActivateSpaceUseCase
            activateSpaceUseCase;

    private final DeactivateSpaceUseCase
            deactivateSpaceUseCase;

    private final UpdateSpaceUseCase
            updateSpaceUseCase;

    private final DeleteSpaceUseCase
            deleteSpaceUseCase;

    private final AssignLabCohortUseCase
            assignLabCohortUseCase;

    private final UnassignLabCohortUseCase
            unassignLabCohortUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSpaceResponse create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole,
            @Valid
            @RequestBody
            CreateSpaceRequest request
    ) {
        return CreateSpaceResponse.from(
                createSpaceUseCase.create(
                        request.toCommand(),
                        userId,
                        globalRole
                )
        );
    }

    @PutMapping("/{spaceId}")
    public UpdateSpaceResponse update(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole,
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
                updateSpaceUseCase.update(
                        spaceId,
                        command,
                        userId,
                        globalRole
                )
        );
    }

    @PatchMapping("/{spaceId}/activate")
    public SpaceStatusResponse activate(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole
    ) {
        return SpaceStatusResponse.from(
                activateSpaceUseCase.activate(spaceId, userId, globalRole)
        );
    }

    @PatchMapping("/{spaceId}/deactivate")
    public SpaceStatusResponse deactivate(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole,
            @Valid @RequestBody DeactivateSpaceRequest request
    ) {
        return SpaceStatusResponse.from(
                deactivateSpaceUseCase.deactivate(
                        spaceId,
                        request.inactiveReason(),
                        userId,
                        globalRole
                )
        );
    }

    @DeleteMapping("/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole
    ) {
        deleteSpaceUseCase.delete(spaceId, userId, globalRole);
    }

    @PutMapping("/{spaceId}/cohort")
    public SpaceCohortResponse assignCohort(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole,
            @Valid @RequestBody AssignLabCohortRequest request
    ) {
        return SpaceCohortResponse.from(
                assignLabCohortUseCase.assignCohort(
                        spaceId,
                        request.cohortId(),
                        userId,
                        globalRole
                )
        );
    }

    @DeleteMapping("/{spaceId}/cohort")
    public SpaceCohortResponse unassignCohort(
            @PathVariable Long spaceId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Global-Role", defaultValue = "USER")
            String globalRole
    ) {
        return SpaceCohortResponse.from(
                unassignLabCohortUseCase.unassignCohort(
                        spaceId,
                        userId,
                        globalRole
                )
        );
    }
}
