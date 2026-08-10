package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;

public record CommunityAttachmentMetadata(
        Long attachmentId,
        String storageKey,
        String originalFileName,
        String contentType,
        long sizeBytes,
        int displayOrder
) {

    public static CommunityAttachmentMetadata from(CommunityPostAttachment attachment) {
        return new CommunityAttachmentMetadata(
                attachment.getId(),
                attachment.getStorageKey(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getDisplayOrder()
        );
    }
}
