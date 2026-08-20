package site.omagotchi.learningservice.community.application.command;

import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;

import java.util.List;

public record UpdateCommunityPostCommand(
        String title,
        String content,
        List<CommunityAttachmentFile> attachments,
        boolean replaceAttachments
) {

    public UpdateCommunityPostCommand(String title, String content) {
        this(title, content, List.of(), false);
    }

    public UpdateCommunityPostCommand {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
