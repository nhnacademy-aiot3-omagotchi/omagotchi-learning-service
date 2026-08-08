package site.omagotchi.learningservice.community.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostDetailResponse;
import site.omagotchi.learningservice.community.presentation.response.CommunityPostPageResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final CommunityPostQueryService communityPostQueryService;

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
}
