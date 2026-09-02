package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

public record CommunityPostListItem(
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
}
