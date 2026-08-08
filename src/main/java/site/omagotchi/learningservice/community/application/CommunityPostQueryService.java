package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CommunityPostQueryPort communityPostQueryPort;

    public CommunityPostPage getPosts(
            UUID userId,
            Integer page,
            Integer size,
            CommunityPostType type,
            String search
    ) {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedPage < 0 || normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new BusinessException(CommunityErrorCode.INVALID_PAGE_REQUEST);
        }

        return communityPostQueryPort.findVisiblePosts(new CommunityPostSearchCondition(
                userId,
                normalizedPage,
                normalizedSize,
                type,
                normalizeSearch(search)
        ));
    }

    public CommunityPostDetail getPost(UUID userId, Long postId) {
        return communityPostQueryPort.findVisiblePost(userId, postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalizedSearch = search.trim();
        return normalizedSearch.isEmpty() ? null : normalizedSearch;
    }
}
