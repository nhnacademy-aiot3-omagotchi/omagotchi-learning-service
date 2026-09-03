package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.infrastructure.CommunityAttachmentProperties;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostAttachmentRepository;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostJpaRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 커뮤니티 게시글 생성/수정/삭제/고정 정책을 담당한다.
 *
 * <p>게시판은 기수 단위이므로 권한은 전부 해당 기수의 membership으로 판정한다.
 * 공지는 MANAGER·MENTOR가, 자유글은 작성자 본인이 다룬다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostCommandService {

    private final CommunityPostJpaRepository communityPostRepository;
    private final CommunityPostAttachmentRepository attachmentRepository;
    private final CohortAccessService cohortAccessService;
    private final CohortLockService cohortLockService;
    private final CommunityAttachmentStorage attachmentStorage;
    private final CommunityAttachmentProperties attachmentProperties;
    private final CommunityAuthorNames communityAuthorNames;
    private final Clock clock;

    /**
     * 게시글과 첨부파일 메타데이터를 같은 트랜잭션에서 저장하고,
     * 파일 저장 성공 후 DB 저장이 실패하면 새 파일을 정리한다.
     */
    @Transactional
    public CommunityPostDetail create(
            UUID userId,
            Long cohortId,
            CreateCommunityPostCommand command
    ) {
        validateWritePermission(userId, cohortId, command.type());
        validateAttachmentCount(command.attachments());
        CommunityPost post = CommunityPost.create(
                command.type(),
                command.title(),
                command.content(),
                userId,
                cohortId
        );
        CommunityPost savedPost = communityPostRepository.saveAndFlush(post);
        List<CommunityPostAttachment> attachments = replaceAttachmentsWithoutCountValidation(
                savedPost.getId(),
                List.of(),
                command.attachments()
        );
        // 방금 만든 글이므로 작성자는 요청자이고 관리 권한도 당연히 있다.
        return CommunityPostDetail.from(savedPost, toMetadata(attachments))
                .withViewer(communityAuthorNames.of(userId), true);
    }

    @Transactional
    public CommunityPostDetail update(
            UUID userId,
            Long cohortId,
            Long postId,
            UpdateCommunityPostCommand command
    ) {
        CommunityPost post = findActivePost(cohortId, postId);
        validateManagePermission(userId, cohortId, post);
        post.update(command.title(), command.content());
        List<CommunityPostAttachment> attachments = command.replaceAttachments()
                ? replaceAttachments(
                post.getId(),
                attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId()),
                command.attachments()
        )
                : attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId());
        // validateManagePermission을 통과했으므로 관리 권한이 있다.
        return CommunityPostDetail.from(post, toMetadata(attachments))
                .withViewer(communityAuthorNames.of(post.getAuthorUserId()), true);
    }

    @Transactional
    public void delete(UUID userId, Long cohortId, Long postId) {
        CommunityPost post = findActivePost(cohortId, postId);
        validateManagePermission(userId, cohortId, post);
        List<CommunityPostAttachment> attachments = attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(
                post.getId()
        );
        post.delete(clock.instant());
        attachmentRepository.deleteByPostId(post.getId());
        deleteStoredAttachmentsAfterCommit(attachments);
    }

    /**
     * 고정은 기수 게시판의 운영 행위다. 공지를 쓸 수 있는 MANAGER·MENTOR가 수행한다.
     *
     * <p>기수의 고정 공지는 하나다. 화면 상단 배너가 한 자리뿐이라, 새로 고정하면
     * 기존 고정은 자동으로 내려간다. 운영자가 이전 것을 먼저 찾아 해제할 필요가 없다.</p>
     */
    @Transactional
    public CommunityPostDetail pin(
            UUID userId,
            Long cohortId,
            Long postId,
            PinCommunityPostCommand command
    ) {
        requireNoticeWriter(userId, cohortId);
        // 기수 행을 공통 mutex로 쓴다. 고정 행이 아직 없는 경우에도 같은 기수의
        // 두 요청이 "전부 해제 → 새 고정" 사이로 끼어들 수 없게 한다.
        cohortLockService.lock(cohortId);
        CommunityPost post = findActivePost(cohortId, postId);
        if (command.pinned()) {
            requireNotice(post);
            communityPostRepository.unpinAll(cohortId);
            // unpinAll이 영속성 컨텍스트를 비우므로, 갱신할 대상은 다시 읽어야 한다.
            post = findActivePost(cohortId, postId);
        }
        post.changePinned(command.pinned());
        // 고정은 MANAGER·MENTOR의 권한이지만, 남의 자유글을 고정했다고 그 글을
        // 수정·삭제할 수 있는 건 아니다.
        return CommunityPostDetail.from(
                post,
                toMetadata(attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId()))
        ).withViewer(
                communityAuthorNames.of(post.getAuthorUserId()),
                post.isNotice() || post.isAuthor(userId)
        );
    }

    /**
     * 다른 기수의 게시글 식별자는 404로 돌려 기수 경계 밖 게시글의 존재를 숨긴다.
     */
    private CommunityPost findActivePost(Long cohortId, Long postId) {
        return communityPostRepository.findByIdAndCohortIdAndDeletedAtIsNull(postId, cohortId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }

    /**
     * 상단 배너는 공지 자리다. 자유글을 고정하면 "공지" 라벨 아래 자유글이 걸린다.
     *
     * <p>관리자 화면이 공지 목록에서만 고정 버튼을 그리므로 UI로는 닿지 않는 경로지만,
     * API를 직접 부르면 가능하므로 서버에서 막는다.</p>
     */
    private void requireNotice(CommunityPost post) {
        if (!post.isNotice()) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_REQUEST);
        }
    }

    private void validateWritePermission(UUID userId, Long cohortId, CommunityPostType type) {
        if (type == null) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_REQUEST);
        }
        if (type == CommunityPostType.NOTICE) {
            requireNoticeWriter(userId, cohortId);
            return;
        }
        requireActiveCohortMember(userId, cohortId);
    }

    private void validateManagePermission(UUID userId, Long cohortId, CommunityPost post) {
        if (post.isNotice()) {
            requireNoticeWriter(userId, cohortId);
            return;
        }
        if (!post.isAuthor(userId)) {
            throw new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED);
        }
        requireActiveCohortMember(userId, cohortId);
    }

    private void requireActiveCohortMember(UUID userId, Long cohortId) {
        if (!cohortAccessService.isActiveMember(cohortId, userId)) {
            throw new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED);
        }
    }

    private void requireNoticeWriter(UUID userId, Long cohortId) {
        if (!cohortAccessService.isActiveManagerOrMentor(cohortId, userId)) {
            throw new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED);
        }
    }

    private List<CommunityPostAttachment> replaceAttachments(
            Long postId,
            List<CommunityPostAttachment> existingAttachments,
            List<CommunityAttachmentFile> newAttachments
    ) {
        validateAttachmentCount(newAttachments);
        return replaceAttachmentsWithoutCountValidation(postId, existingAttachments, newAttachments);
    }

    private List<CommunityPostAttachment> replaceAttachmentsWithoutCountValidation(
            Long postId,
            List<CommunityPostAttachment> existingAttachments,
            List<CommunityAttachmentFile> newAttachments
    ) {
        List<StoredCommunityAttachment> storedAttachments = new ArrayList<>();
        try {
            for (var attachmentFile : newAttachments) {
                storedAttachments.add(attachmentStorage.store(attachmentFile));
            }

            if (!existingAttachments.isEmpty()) {
                attachmentRepository.deleteByPostId(postId);
            }
            List<CommunityPostAttachment> attachments = storedAttachments.stream()
                    .map(attachment -> CommunityPostAttachment.create(
                            postId,
                            attachment.storageKey(),
                            attachment.originalFileName(),
                            attachment.contentType(),
                            attachment.sizeBytes(),
                            attachment.displayOrder()
                    ))
                    .toList();
            List<CommunityPostAttachment> savedAttachments = attachmentRepository.saveAllAndFlush(attachments);
            deleteStoredAttachmentsAfterCommit(existingAttachments);
            return savedAttachments;
        } catch (RuntimeException exception) {
            // 정리하다 저장소가 또 실패해도 원래 실패를 가리지 않는다. 객체 스토리지는
            // 네트워크 너머라 삭제도 실패할 수 있고, 그때 남는 고아 객체보다 원인을
            // 잃는 쪽이 더 나쁘다.
            storedAttachments.forEach(attachment -> {
                try {
                    attachmentStorage.delete(attachment.storageKey());
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            });
            throw exception;
        }
    }

    private void validateAttachmentCount(List<CommunityAttachmentFile> attachments) {
        if (attachments.size() > attachmentProperties.maxCount()) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
    }

    private List<CommunityAttachmentMetadata> toMetadata(List<CommunityPostAttachment> attachments) {
        return attachments.stream()
                .map(CommunityAttachmentMetadata::from)
                .toList();
    }

    /**
     * 커밋이 끝난 뒤 부르는 정리라서 실패를 밖으로 올리지 않는다.
     * 여기에서 예외를 던지면 이미 성공한 요청이 500이 된다. 고아 객체를 남기고 로그만 남긴다.
     */
    private void deleteStoredAttachments(List<CommunityPostAttachment> attachments) {
        attachments.forEach(attachment -> {
            try {
                attachmentStorage.delete(attachment.getStorageKey());
            } catch (RuntimeException exception) {
                log.warn(
                        "첨부파일 객체를 지우지 못했습니다. postId={}, key={}",
                        attachment.getPostId(),
                        attachment.getStorageKey(),
                        exception
                );
            }
        });
    }

    private void deleteStoredAttachmentsAfterCommit(List<CommunityPostAttachment> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStoredAttachments(attachments);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStoredAttachments(attachments);
            }
        });
    }
}
