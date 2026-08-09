package site.omagotchi.learningservice.community.application.query;

import java.util.List;

public record CommunityPostPage(
        List<CommunityPostListItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
