package site.omagotchi.learningservice.community.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

public record CreateCommunityPostRequest(
        @NotNull(message = "게시글 유형은 필수입니다.")
        CommunityPostType type,

        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 100, message = "게시글 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "게시글 내용은 필수입니다.")
        @Size(max = 10000, message = "게시글 내용은 10000자 이하여야 합니다.")
        String content,

        @NotNull(message = "게시글 공개 범위는 필수입니다.")
        CommunityPostScope scope,

        Long cohortId
) {

    public CreateCommunityPostCommand toCommand() {
        return new CreateCommunityPostCommand(
                type,
                title,
                content,
                scope,
                cohortId
        );
    }
}
