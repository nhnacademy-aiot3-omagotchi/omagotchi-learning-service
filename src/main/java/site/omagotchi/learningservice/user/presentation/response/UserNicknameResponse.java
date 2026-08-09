package site.omagotchi.learningservice.user.presentation.response;

import site.omagotchi.learningservice.user.application.result.UserNicknameResult;

public record UserNicknameResponse(
        String nickname
) {

    public static UserNicknameResponse from(UserNicknameResult result) {
        return new UserNicknameResponse(result.nickname());
    }
}
