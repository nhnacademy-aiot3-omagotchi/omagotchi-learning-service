package site.omagotchi.learningservice.community.application.command;

import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;

import java.util.List;

public record CreateCommunityPostCommand(
        CommunityPostType type,
        String title,
        String content,
        CommunityPostScope scope,
        Long cohortId,
        List<CommunityAttachmentFile> attachments
) {

    public CreateCommunityPostCommand(
            CommunityPostType type,
            String title,
            String content,
            CommunityPostScope scope,
            Long cohortId
    ) {
        this(type, title, content, scope, cohortId, List.of());
    }

    public CreateCommunityPostCommand {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
