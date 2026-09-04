package site.omagotchi.learningservice.community.application.attachment;

import org.springframework.core.io.Resource;

import java.util.Optional;

public interface CommunityAttachmentStorage {

    StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile);

    Resource load(String storageKey);

    Optional<Resource> loadThumbnail(String storageKey);

    void delete(String storageKey);
}
