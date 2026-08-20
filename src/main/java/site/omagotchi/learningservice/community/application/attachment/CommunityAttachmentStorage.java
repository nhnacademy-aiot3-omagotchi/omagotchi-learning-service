package site.omagotchi.learningservice.community.application.attachment;

public interface CommunityAttachmentStorage {

    StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile);

    void delete(String storageKey);
}
