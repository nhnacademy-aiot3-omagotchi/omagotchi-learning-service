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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.ActivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeactivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.presentation.request.CreateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.request.DeactivateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.request.UpdateSpaceRequest;
import site.omagotchi.learningservice.space.presentation.response.CreateSpaceResponse;
import site.omagotchi.learningservice.space.presentation.response.SpaceStatusResponse;
import site.omagotchi.learningservice.space.presentation.response.UpdateSpaceResponse;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSpaceResponse create(
            @Valid
            @RequestBody
            CreateSpaceRequest request
    ) {
        CreateSpaceCommand command =
                new CreateSpaceCommand(
                        request.name(),
                        request.type(),
                        request.capacity()
                );

        return CreateSpaceResponse.from(
                createSpaceUseCase.create(command)
        );
    }

    @PutMapping("/{spaceId}")
    public UpdateSpaceResponse update(
            @PathVariable Long spaceId,
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
                        command
                )
        );
    }

    @PatchMapping("/{spaceId}/activate")
    public SpaceStatusResponse activate(
            @PathVariable Long spaceId
    ) {
        return SpaceStatusResponse.from(
                activateSpaceUseCase.activate(spaceId)
        );
    }

    @PatchMapping("/{spaceId}/deactivate")
    public SpaceStatusResponse deactivate(
            @PathVariable Long spaceId,
            @Valid @RequestBody DeactivateSpaceRequest request
    ) {
        return SpaceStatusResponse.from(
                deactivateSpaceUseCase.deactivate(
                        spaceId,
                        request.inactiveReason()
                )
        );
    }

    @DeleteMapping("/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long spaceId
    ) {
        deleteSpaceUseCase.delete(spaceId);
    }
}
