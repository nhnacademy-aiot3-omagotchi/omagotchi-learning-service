package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;

public record CommunityPostAttachmentResponse(
        Long attachmentId,
        String originalFileName,
        String contentType,
        long sizeBytes,
        int displayOrder
) {

    public static CommunityPostAttachmentResponse from(CommunityAttachmentMetadata metadata) {
        return new CommunityPostAttachmentResponse(
                metadata.attachmentId(),
                metadata.originalFileName(),
                metadata.contentType(),
                metadata.sizeBytes(),
                metadata.displayOrder()
        );
    }
}
