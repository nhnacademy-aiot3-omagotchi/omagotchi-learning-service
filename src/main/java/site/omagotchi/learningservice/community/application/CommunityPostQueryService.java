package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentDownload;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.UUID;

/**
 * 커뮤니티 게시글 조회 조건을 정규화하고, 실제 가시성/검색/페이징 처리는 query port에 위임한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CommunityPostQueryPort communityPostQueryPort;
    private final CommunityAttachmentStorage communityAttachmentStorage;

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

    public CommunityAttachmentDownload downloadAttachment(UUID userId, Long postId, Long attachmentId) {
        CommunityPostDetail post = getPost(userId, postId);
        var attachment = post.attachments().stream()
                .filter(candidate -> candidate.attachmentId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.ATTACHMENT_NOT_FOUND));

        return new CommunityAttachmentDownload(
                attachment.originalFileName(),
                attachment.contentType(),
                attachment.sizeBytes(),
                communityAttachmentStorage.load(attachment.storageKey())
        );
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalizedSearch = search.trim();
        return normalizedSearch.isEmpty() ? null : normalizedSearch;
    }
}
