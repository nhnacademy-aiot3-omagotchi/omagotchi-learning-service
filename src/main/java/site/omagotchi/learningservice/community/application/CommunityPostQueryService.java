package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentDownload;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.UUID;

/**
 * 커뮤니티 게시글 조회 조건을 정규화하고, 실제 가시성/검색/페이징 처리는 query port에 위임한다.
 *
 * <p>게시판은 기수 단위다. 모든 진입에서 해당 기수의 ACTIVE 소속을 먼저 확인하고,
 * 소속이 없으면 기수 존재를 숨기기 위해 404로 끊는다. 그래서 조회 쿼리는 membership을
 * 다시 확인하지 않는다.</p>
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
    private final CohortAccessService cohortAccessService;

    public CommunityPostPage getPosts(
            UUID userId,
            Long cohortId,
            Integer page,
            Integer size,
            CommunityPostType type,
            String search
    ) {
        cohortAccessService.requireActiveMembership(cohortId, userId);

        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedPage < 0 || normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new BusinessException(CommunityErrorCode.INVALID_PAGE_REQUEST);
        }

        return communityPostQueryPort.findVisiblePosts(new CommunityPostSearchCondition(
                cohortId,
                normalizedPage,
                normalizedSize,
                type,
                normalizeSearch(search)
        ));
    }

    public CommunityPostDetail getPost(UUID userId, Long cohortId, Long postId) {
        cohortAccessService.requireActiveMembership(cohortId, userId);
        return communityPostQueryPort.findVisiblePost(cohortId, postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }

    public CommunityAttachmentDownload downloadAttachment(
            UUID userId,
            Long cohortId,
            Long postId,
            Long attachmentId
    ) {
        CommunityPostDetail post = getPost(userId, cohortId, postId);
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
