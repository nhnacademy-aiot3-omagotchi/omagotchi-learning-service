package site.omagotchi.learningservice.community.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.util.List;

/**
 * 소속 기수는 경로 변수로 받는다. Browser가 본문으로 다른 기수를 지정할 수 없다.
 */
public record CreateCommunityPostRequest(
        @NotNull(message = "게시글 유형은 필수입니다.")
        CommunityPostType type,

        @NotBlank(message = "게시글 제목은 필수입니다.")
        @Size(max = 100, message = "게시글 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "게시글 내용은 필수입니다.")
        @Size(max = 10000, message = "게시글 내용은 10000자 이하여야 합니다.")
        String content
) {

    public CreateCommunityPostCommand toCommand() {
        return toCommand(List.of());
    }

    public CreateCommunityPostCommand toCommand(List<CommunityAttachmentFile> attachments) {
        return new CreateCommunityPostCommand(type, title, content, attachments);
    }
}
