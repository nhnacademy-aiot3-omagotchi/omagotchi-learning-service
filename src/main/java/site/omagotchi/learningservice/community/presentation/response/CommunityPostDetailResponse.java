package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunityPostDetailResponse(
        Long postId,
        CommunityPostType type,
        String title,
        String content,
        UUID authorUserId,
        Long cohortId,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityPostAttachmentResponse> attachments
) {

    public static CommunityPostDetailResponse from(CommunityPostDetail detail) {
        return new CommunityPostDetailResponse(
                detail.postId(),
                detail.type(),
                detail.title(),
                detail.content(),
                detail.authorUserId(),
                detail.cohortId(),
                detail.pinned(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.attachments().stream()
                        .map(CommunityPostAttachmentResponse::from)
                        .toList()
        );
    }
}
