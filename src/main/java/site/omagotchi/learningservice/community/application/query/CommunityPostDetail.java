package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostDetail(
        Long postId,
        CommunityPostType type,
        String title,
        String content,
        UUID authorUserId,
        CommunityPostScope scope,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommunityPostDetail from(CommunityPost post) {
        return new CommunityPostDetail(
                post.getId(),
                post.getType(),
                post.getTitle(),
                post.getContent(),
                post.getAuthorUserId(),
                post.getScope(),
                post.getCohortId(),
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
