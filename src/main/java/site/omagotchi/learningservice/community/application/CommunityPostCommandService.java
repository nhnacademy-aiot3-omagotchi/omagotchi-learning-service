package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * 커뮤니티 게시글 생성/수정/삭제/고정 정책을 담당한다.
 *
 * <p>게시판은 기수 단위이므로 권한은 전부 해당 기수의 membership으로 판정한다.
 * 공지는 MANAGER·MENTOR가, 자유글은 작성자 본인이 다룬다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostCommandService {

    private static final Set<CohortMembershipRole> NOTICE_WRITER_ROLES = Set.of(
            CohortMembershipRole.MANAGER,
            CohortMembershipRole.MENTOR
    );

    private final CommunityPostJpaRepository communityPostRepository;
    private final CommunityPostAttachmentRepository attachmentRepository;
    private final CohortMembershipRepository cohortMembershipRepository;
    private final CommunityAttachmentStorage attachmentStorage;
    private final CommunityAttachmentProperties attachmentProperties;
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
        return CommunityPostDetail.from(savedPost, toMetadata(attachments));
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
        return CommunityPostDetail.from(post, toMetadata(attachments));
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
     */
    @Transactional
    public CommunityPostDetail pin(
            UUID userId,
            Long cohortId,
            Long postId,
            PinCommunityPostCommand command
    ) {
        requireNoticeWriter(userId, cohortId);
        CommunityPost post = findActivePost(cohortId, postId);
        post.changePinned(command.pinned());
        return CommunityPostDetail.from(
                post,
                toMetadata(attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId()))
        );
    }

    /**
     * 다른 기수의 게시글 식별자는 404로 돌려 기수 경계 밖 게시글의 존재를 숨긴다.
     */
    private CommunityPost findActivePost(Long cohortId, Long postId) {
        return communityPostRepository.findByIdAndCohortIdAndDeletedAtIsNull(postId, cohortId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
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
        boolean activeMember = cohortMembershipRepository.existsByCohortIdAndUserIdAndStatusIn(
                cohortId,
                userId,
                Set.of(CohortMembershipStatus.ACTIVE)
        );
        if (!activeMember) {
            throw new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED);
        }
    }

    private void requireNoticeWriter(UUID userId, Long cohortId) {
        boolean noticeWriter = cohortMembershipRepository.existsByCohortIdAndUserIdAndRoleInAndStatus(
                cohortId,
                userId,
                NOTICE_WRITER_ROLES,
                CohortMembershipStatus.ACTIVE
        );
        if (!noticeWriter) {
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
            storedAttachments.forEach(attachment -> attachmentStorage.delete(attachment.storageKey()));
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

    private void deleteStoredAttachments(List<CommunityPostAttachment> attachments) {
        attachments.forEach(attachment -> attachmentStorage.delete(attachment.getStorageKey()));
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
