package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

@ConfigurationProperties(prefix = "community.attachments")
public record CommunityAttachmentProperties(
        String bucket,
        DataSize maxFileSize,
        int maxCount,
        List<String> allowedExtensions,
        List<String> allowedContentTypes
) {

    public CommunityAttachmentProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "community-attachments";
        }
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(5);
        }
        if (maxCount <= 0) {
            maxCount = 5;
        }
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            allowedExtensions = List.of("jpg", "jpeg", "png", "gif");
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = List.of("image/jpeg", "image/png", "image/gif");
        }
    }
}
