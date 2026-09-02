package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentDownload;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 커뮤니티 게시글 조회 조건을 정규화하고, 실제 가시성/검색/페이징 처리는 query port에 위임한다.
 *
 * <p>게시판은 기수 단위다. 모든 진입에서 해당 기수의 ACTIVE 소속을 먼저 확인하고,
 * 소속이 없으면 기수 존재를 숨기기 위해 404로 끊는다. 그래서 조회 쿼리는 membership을
 * 다시 확인하지 않는다.</p>
 *
 * <p>작성자 이름과 관리 권한은 보는 사람에 따라 달라지므로 저장소가 아니라 여기에서 채운다.
 * 권한 판정에 필요한 역할은 소속 확인에서 이미 받아오므로 추가 조회가 없다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    // CommunityPostCommandService의 수정·삭제 판정과 같은 규칙이다.
    private static final Set<CohortMembershipRole> NOTICE_WRITER_ROLES = Set.of(
            CohortMembershipRole.MANAGER,
            CohortMembershipRole.MENTOR
    );

    private final CommunityPostQueryPort communityPostQueryPort;
    private final CommunityAttachmentStorage communityAttachmentStorage;
    private final CohortAccessService cohortAccessService;
    private final CommunityAuthorNames communityAuthorNames;

    public CommunityPostPage getPosts(
            UUID userId,
            Long cohortId,
            Integer page,
            Integer size,
            CommunityPostType type,
            String search
    ) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);

        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedPage < 0 || normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new BusinessException(CommunityErrorCode.INVALID_PAGE_REQUEST);
        }

        CommunityPostPage found = communityPostQueryPort.findVisiblePosts(new CommunityPostSearchCondition(
                cohortId,
                normalizedPage,
                normalizedSize,
                type,
                normalizeSearch(search)
        ));

        boolean noticeWriter = isNoticeWriter(membership);
        Map<UUID, String> nicknames = communityAuthorNames.of(found.items().stream()
                .map(CommunityPostListItem::authorUserId)
                .collect(Collectors.toSet()));
        List<CommunityPostListItem> items = found.items().stream()
                .map(item -> item.withViewer(
                        nicknames.get(item.authorUserId()),
                        canManage(item.type(), item.authorUserId(), userId, noticeWriter)
                ))
                .toList();

        return new CommunityPostPage(
                items,
                found.page(),
                found.size(),
                found.totalElements(),
                found.totalPages()
        );
    }

    public CommunityPostDetail getPost(UUID userId, Long cohortId, Long postId) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);
        CommunityPostDetail detail = findVisiblePost(cohortId, postId);
        return detail.withViewer(
                communityAuthorNames.of(detail.authorUserId()),
                canManage(detail.type(), detail.authorUserId(), userId, isNoticeWriter(membership))
        );
    }

    /**
     * 다운로드는 작성자 이름이나 관리 권한을 쓰지 않으므로 그 조회를 생략한다.
     */
    public CommunityAttachmentDownload downloadAttachment(
            UUID userId,
            Long cohortId,
            Long postId,
            Long attachmentId
    ) {
        cohortAccessService.requireActiveMembership(cohortId, userId);
        CommunityPostDetail post = findVisiblePost(cohortId, postId);
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

    private CommunityPostDetail findVisiblePost(Long cohortId, Long postId) {
        return communityPostQueryPort.findVisiblePost(cohortId, postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private boolean isNoticeWriter(CohortMembership membership) {
        return NOTICE_WRITER_ROLES.contains(membership.getRole());
    }

    /**
     * 공지는 MANAGER·MENTOR가, 자유글은 작성자 본인이 다룬다.
     * 이 기수의 ACTIVE 소속인지는 진입에서 이미 확인했다.
     */
    private boolean canManage(
            CommunityPostType type,
            UUID authorUserId,
            UUID viewerUserId,
            boolean noticeWriter
    ) {
        return type == CommunityPostType.NOTICE
                ? noticeWriter
                : authorUserId.equals(viewerUserId);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalizedSearch = search.trim();
        return normalizedSearch.isEmpty() ? null : normalizedSearch;
    }
}
