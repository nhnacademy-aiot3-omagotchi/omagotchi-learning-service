package site.omagotchi.learningservice.community.application.attachment;

import org.springframework.core.io.Resource;

public record CommunityAttachmentDownload(
        String originalFileName,
        String contentType,
        long sizeBytes,
        Resource resource
) {
}
