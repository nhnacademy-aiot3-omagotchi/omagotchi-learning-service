package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostPage;

import java.util.List;

public record CommunityPostPageResponse(
        List<CommunityPostListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static CommunityPostPageResponse from(CommunityPostPage page) {
        return new CommunityPostPageResponse(
                page.items().stream()
                        .map(CommunityPostListItemResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
