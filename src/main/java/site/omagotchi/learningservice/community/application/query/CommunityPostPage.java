package site.omagotchi.learningservice.community.application.query;

import java.util.List;

/**
 * @param items  고정 공지를 제외한 목록. 화면 배너와 목록에 같은 글이 두 번 나오지 않게 한다.
 * @param pinned 기수의 고정 공지. 없으면 null이며, 필터·검색·페이지와 무관하게 항상 같은 글이다.
 */
public record CommunityPostPage(
        List<CommunityPostListItem> items,
        CommunityPostListItem pinned,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
