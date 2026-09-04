package site.omagotchi.learningservice.community.application.attachment;

import org.springframework.core.io.Resource;

public record CommunityAttachmentPreview(
        String contentType,
        Resource resource
) {
}
