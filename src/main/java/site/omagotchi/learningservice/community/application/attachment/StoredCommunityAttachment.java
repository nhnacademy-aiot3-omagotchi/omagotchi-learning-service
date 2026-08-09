package site.omagotchi.learningservice.community.application.attachment;

public record StoredCommunityAttachment(
        String storageKey,
        String originalFileName,
        String contentType,
        long sizeBytes,
        int displayOrder
) {
}
