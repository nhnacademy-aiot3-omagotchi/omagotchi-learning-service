package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostListItemResponse(
        Long postId,
        CommunityPostType type,
        String title,
        UUID authorUserId,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        long attachmentCount
) {

    public static CommunityPostListItemResponse from(CommunityPostListItem item) {
        return new CommunityPostListItemResponse(
                item.postId(),
                item.type(),
                item.title(),
                item.authorUserId(),
                item.cohortId(),
                item.pinned(),
                item.createdAt(),
                item.updatedAt(),
                item.attachmentCount()
        );
    }
}
