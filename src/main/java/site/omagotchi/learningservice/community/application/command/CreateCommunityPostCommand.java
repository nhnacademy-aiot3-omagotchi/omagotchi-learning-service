package site.omagotchi.learningservice.community.application.command;

import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.util.List;

/**
 * 게시글 생성 명령. 소속 기수는 경로에서 오므로 여기에 담지 않는다.
 */
public record CreateCommunityPostCommand(
        CommunityPostType type,
        String title,
        String content,
        List<CommunityAttachmentFile> attachments
) {

    public CreateCommunityPostCommand(
            CommunityPostType type,
            String title,
            String content
    ) {
        this(type, title, content, List.of());
    }

    public CreateCommunityPostCommand {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
