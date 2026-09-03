package site.omagotchi.learningservice.community.application.query;

import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param authorNickname 대표 캐릭터가 없으면 null이다.
 * @param canManage      이 게시글을 수정·삭제할 수 있는지. 보는 사람에 따라 달라진다.
 */
public record CommunityPostDetail(
        Long postId,
        CommunityPostType type,
        String title,
        String content,
        UUID authorUserId,
        String authorNickname,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityAttachmentMetadata> attachments,
        boolean canManage
) {

    public CommunityPostDetail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
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
                null,
                post.getCohortId(),
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                attachments,
                false
        );
    }

    public CommunityPostDetail withViewer(String authorNickname, boolean canManage) {
        return new CommunityPostDetail(
                postId, type, title, content, authorUserId, authorNickname, cohortId,
                pinned, createdAt, updatedAt, attachments, canManage
        );
    }
}
