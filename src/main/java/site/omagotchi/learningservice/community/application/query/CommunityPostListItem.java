package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostListItem(
        Long postId,
        CommunityPostType type,
        String title,
        UUID authorUserId,
        CommunityPostScope scope,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommunityPostListItem from(CommunityPost post) {
        return new CommunityPostListItem(
                post.getId(),
                post.getType(),
                post.getTitle(),
                post.getAuthorUserId(),
                post.getScope(),
                post.getCohortId(),
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
