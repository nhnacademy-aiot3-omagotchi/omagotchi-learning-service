package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;

/**
 * 작성자는 표시 이름만 내보낸다. 내부 식별자(UUID)를 목록에 실을 이유가 없고,
 * 본인 글 판정은 {@code canManage}가 대신한다.
 */
public record CommunityPostListItemResponse(
        Long postId,
        CommunityPostType type,
        String title,
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
