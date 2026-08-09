package site.omagotchi.learningservice.community.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;

public record PinCommunityPostRequest(
        @NotNull(message = "고정 여부는 필수입니다.")
        Boolean pinned
) {

    public PinCommunityPostCommand toCommand() {
        return new PinCommunityPostCommand(pinned);
    }
}
