package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.UUID;

/**
 * @param authorNickname 조회 시점에 채운다. 대표 캐릭터가 없으면 null이다.
 * @param canManage      이 게시글을 수정·삭제할 수 있는지. 보는 사람에 따라 달라진다.
 */
public record CommunityPostListItem(
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

    /**
     * 저장소 프로젝션용. 보는 사람에 따라 달라지는 값은 조회 서비스가 채운다.
     */
    public CommunityPostListItem(
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
        this(postId, type, title, authorUserId, null, cohortId, pinned, createdAt, updatedAt, attachmentCount, false);
    }

    public CommunityPostListItem withViewer(String authorNickname, boolean canManage) {
        return new CommunityPostListItem(
                postId, type, title, authorUserId, authorNickname, cohortId,
                pinned, createdAt, updatedAt, attachmentCount, canManage
        );
    }
}
