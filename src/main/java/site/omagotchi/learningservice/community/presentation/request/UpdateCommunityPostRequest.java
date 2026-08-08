package site.omagotchi.learningservice.community.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;

public record UpdateCommunityPostRequest(
        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 100, message = "게시글 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "게시글 내용은 필수입니다.")
        @Size(max = 10000, message = "게시글 내용은 10000자 이하여야 합니다.")
        String content
) {

    public UpdateCommunityPostCommand toCommand() {
        return new UpdateCommunityPostCommand(title, content);
    }
}
