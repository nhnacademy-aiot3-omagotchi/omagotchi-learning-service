package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunityPostDetail(
        Long postId,
        CommunityPostType type,
        String title,
        String content,
        UUID authorUserId,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityAttachmentMetadata> attachments
) {

    public CommunityPostDetail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static CommunityPostDetail from(CommunityPost post) {
        return from(post, List.of());
    }

    public static CommunityPostDetail from(
            CommunityPost post,
            List<CommunityAttachmentMetadata> attachments
    ) {
        return new CommunityPostDetail(
                post.getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorUserId(),
                post.getCohortId(),
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                attachments
        );
    }
}
