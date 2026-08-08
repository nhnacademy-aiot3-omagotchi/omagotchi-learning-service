package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostListItemResponse(
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

    public static CommunityPostListItemResponse from(CommunityPostListItem item) {
        return new CommunityPostListItemResponse(
                item.postId(),
                item.type(),
                item.title(),
                item.authorUserId(),
                item.scope(),
                item.cohortId(),
                item.pinned(),
                item.createdAt(),
                item.updatedAt()
        );
    }
}
