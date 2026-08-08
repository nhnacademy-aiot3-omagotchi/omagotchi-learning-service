package site.omagotchi.learningservice.community.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.community.application.CommunityPostCommandService;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.presentation.request.CreateCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.request.PinCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.request.UpdateCommunityPostRequest;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostDetailResponse;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostPageResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final CommunityPostQueryService communityPostQueryService;
    private final CommunityPostCommandService communityPostCommandService;

    @GetMapping
    public CommunityPostPageResponse getPosts(
            JwtAuthenticationToken authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) CommunityPostType type,
            @RequestParam(required = false) String search
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostPageResponse.from(communityPostQueryService.getPosts(
                user.userId(),
                page,
                size,
                type,
                search
        ));
    }

    @GetMapping("/{postId}")
    public CommunityPostDetailResponse getPost(
            JwtAuthenticationToken authentication,
            @PathVariable Long postId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostQueryService.getPost(
                user.userId(),
                postId
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostDetailResponse create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.create(
                user.userId(),
                user.globalRole(),
                request.toCommand()
        ));
    }

    @PatchMapping("/{postId}")
    public CommunityPostDetailResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable Long postId,
            @Valid @RequestBody UpdateCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.update(
                user.userId(),
                user.globalRole(),
                postId,
                request.toCommand()
        ));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            JwtAuthenticationToken authentication,
            @PathVariable Long postId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        communityPostCommandService.delete(
                user.userId(),
                user.globalRole(),
                postId
        );
    }

    @PatchMapping("/{postId}/pin")
    public CommunityPostDetailResponse pin(
            JwtAuthenticationToken authentication,
            @PathVariable Long postId,
            @Valid @RequestBody PinCommunityPostRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return CommunityPostDetailResponse.from(communityPostCommandService.pin(
                user.userId(),
                user.globalRole(),
                postId,
                request.toCommand()
        ));
    }
}
