package site.omagotchi.learningservice.user.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.user.application.UserProfileService;
import site.omagotchi.learningservice.user.presentation.request.UpdateNicknameRequest;
import site.omagotchi.learningservice.user.presentation.response.UserNicknameResponse;
import site.omagotchi.learningservice.user.presentation.response.UserProfileResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public UserProfileResponse getMyProfile(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return UserProfileResponse.from(userProfileService.getMyProfile(user.userId()));
    }

    @PatchMapping("/nickname")
    public UserNicknameResponse updateNickname(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateNicknameRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return UserNicknameResponse.from(userProfileService.updateNickname(
                user.userId(),
                request.nickname()
        ));
    }
}
