package site.omagotchi.learningservice.community.presentation.response;

import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.global.presentation.response.PageInfo;

import java.util.List;

/**
 * @param items  고정 공지를 제외한 목록
 * @param pinned 기수의 고정 공지. 없으면 null이고, 필터·검색·페이지와 무관하게 늘 같은 글이다.
 */
public record CommunityPostPageResponse(
        List<CommunityPostListItemResponse> items,
        CommunityPostListItemResponse pinned,
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
                page.pinned() == null ? null : CommunityPostListItemResponse.from(page.pinned()),
                new PageInfo(
                        page.page(),
                        page.size(),
                        page.totalElements(),
                        page.totalPages()
                )
        );
    }
}
