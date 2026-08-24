package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.global.presentation.response.PageInfo;

import java.util.List;

public record CommunityPostPageResponse(
        List<CommunityPostListItemResponse> items,
        PageInfo page
) {

    public CommunityPostPageResponse {
        items = List.copyOf(items);
    }

    public static CommunityPostPageResponse from(CommunityPostPage page) {
        return new CommunityPostPageResponse(
                page.items().stream()
                        .map(CommunityPostListItemResponse::from)
                        .toList(),
                new PageInfo(
                        page.page(),
                        page.size(),
                        page.totalElements(),
                        page.totalPages()
                )
        );
    }
}
