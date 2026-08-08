package site.omagotchi.learningservice.community.application.attachment;

import org.springframework.web.multipart.MultipartFile;

public record CommunityAttachmentFile(
        MultipartFile file,
        int displayOrder
) {
}
