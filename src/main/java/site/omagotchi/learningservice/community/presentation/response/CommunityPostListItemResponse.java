package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

/**
 * 본인 글 판정은 {@code canManage}를 쓴다. {@code authorUserId}로 비교하지 않아도 되고,
 * 클라이언트에는 애초에 자기 식별자가 없다.
 */
public record CommunityPostListItemResponse(
        Long postId,
        CommunityPostType type,
        String title,
        UUID authorUserId,
        String authorNickname,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        long attachmentCount,
        boolean canManage
) {

    public static CommunityPostListItemResponse from(CommunityPostListItem item) {
        return new CommunityPostListItemResponse(
                item.postId(),
                item.type(),
                item.title(),
                item.authorUserId(),
                item.authorNickname(),
                item.cohortId(),
                item.pinned(),
                item.createdAt(),
                item.updatedAt(),
                item.attachmentCount(),
                item.canManage()
        );
    }
}
