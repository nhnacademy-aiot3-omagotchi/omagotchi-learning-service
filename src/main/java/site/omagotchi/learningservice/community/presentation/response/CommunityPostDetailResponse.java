package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.List;

public record CommunityPostDetailResponse(
        Long postId,
        CommunityPostType type,
        String title,
        String content,
        String authorNickname,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityPostAttachmentResponse> attachments,
        boolean canManage
) {

    public static CommunityPostDetailResponse from(CommunityPostDetail detail) {
        return new CommunityPostDetailResponse(
                detail.postId(),
                detail.type(),
                detail.title(),
                detail.content(),
                detail.authorNickname(),
                detail.cohortId(),
                detail.pinned(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.attachments().stream()
                        .map(CommunityPostAttachmentResponse::from)
                        .toList(),
                detail.canManage()
        );
    }
}
