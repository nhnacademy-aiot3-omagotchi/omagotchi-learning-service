package site.omagotchi.learningservice.community.application.attachment;

import org.springframework.core.io.Resource;

public interface CommunityAttachmentStorage {

    StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile);

    Resource load(String storageKey);

    void delete(String storageKey);
}
