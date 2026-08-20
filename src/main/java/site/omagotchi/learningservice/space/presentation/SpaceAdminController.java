package site.omagotchi.learningservice.space.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/v1/admin/spaces")
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
                        user.userId()
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
                        user.userId()
                )
        );
    }

    /**
     * 공간 활성화.
     *
     * <p>{@code PATCH}가 아니라 {@code POST} + 동사형 경로인 것은 이것이 속성의 부분 수정이
     * 아니라 상태 전이이기 때문이다 (09-rest-api-convention). 점유의 {@code POST /extend},
     * 팀의 {@code POST /{member-id}/delegate}와 같은 규약이다.</p>
     */
    @PostMapping("/{space-id}/activate")
    public SpaceStatusResponse activate(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return SpaceStatusResponse.from(
                spaceCommandService.activate(
                        spaceId,
                        user.userId()
                )
        );
    }

    /** 공간 비활성화. {@link #activate}와 같은 이유로 상태 전이 규약을 따른다. */
    @PostMapping("/{space-id}/deactivate")
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
                        user.userId()
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
                user.userId()
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
                        user.userId()
                )
        );
    }

    /**
     * 실습실 배정 해제.
     *
     * <p>{@code 204}인 것은 컨벤션이 {@code DELETE}에 대해 정한 상태다
     * (09-rest-api-convention). 본문을 싣지 않아도 잃는 것이 없다 — 해제 후 {@code cohortId}는
     * 항상 {@code null}이라 응답으로 알려줄 새 정보가 없다.</p>
     */
    @DeleteMapping("/{space-id}/cohort")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignCohort(
            @PathVariable("space-id") Long spaceId,
            Authentication authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        spaceCommandService.unassignCohort(
                spaceId,
                user.userId()
        );
    }
}
