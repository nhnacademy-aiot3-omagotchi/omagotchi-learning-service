package site.omagotchi.learningservice.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "community_post_attachments", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommunityPostAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "storage_key", nullable = false, length = 300, updatable = false)
    private String storageKey;

    @Column(name = "original_file_name", nullable = false, length = 255, updatable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CommunityPostAttachment create(
            Long postId,
            String storageKey,
            String originalFileName,
            String contentType,
            long sizeBytes,
            int displayOrder
    ) {
        CommunityPostAttachment attachment = new CommunityPostAttachment();
        attachment.postId = requirePostId(postId);
        attachment.storageKey = requireText(storageKey, "storageKey");
        attachment.originalFileName = requireText(originalFileName, "originalFileName");
        attachment.contentType = requireText(contentType, "contentType");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("첨부파일 크기는 양수여야 합니다.");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("첨부파일 순서는 0 이상이어야 합니다.");
        }
        attachment.sizeBytes = sizeBytes;
        attachment.displayOrder = displayOrder;
        return attachment;
    }

    private static Long requirePostId(Long postId) {
        if (postId == null) {
            throw new IllegalArgumentException("게시글 식별자는 필수입니다.");
        }
        return postId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value.trim();
    }
}
