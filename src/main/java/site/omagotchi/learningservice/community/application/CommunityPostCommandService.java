package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostAttachment;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.infrastructure.CommunityAttachmentProperties;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostAttachmentRepository;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostJpaRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 커뮤니티 게시글 생성/수정/삭제/고정 정책을 담당한다.
 *
 * <p>STUDENT, MENTOR, MANAGER, SYSTEM_ADMIN 권한 차이를 서버의 cohort membership과 JWT role로 검증한다.</p>
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
    private final CohortRepository cohortRepository;
    private final CohortAccessService cohortAccessService;
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
            GlobalRole globalRole,
            CreateCommunityPostCommand command
    ) {
        validateCreatePermission(userId, globalRole, command);
        validateAttachmentCount(command.attachments());
        CommunityPost post = CommunityPost.create(
                command.type(),
                command.title(),
                command.content(),
                userId,
                command.scope(),
                command.cohortId()
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
            GlobalRole globalRole,
            Long postId,
            UpdateCommunityPostCommand command
    ) {
        CommunityPost post = findActivePost(postId);
        validateManagePermission(userId, globalRole, post);
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
    public void delete(UUID userId, GlobalRole globalRole, Long postId) {
        CommunityPost post = findActivePost(postId);
        validateManagePermission(userId, globalRole, post);
        List<CommunityPostAttachment> attachments = attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(
                post.getId()
        );
        post.delete(clock.instant());
        attachmentRepository.deleteByPostId(post.getId());
        deleteStoredAttachmentsAfterCommit(attachments);
    }

    @Transactional
    public CommunityPostDetail pin(
            UUID userId,
            GlobalRole globalRole,
            Long postId,
            PinCommunityPostCommand command
    ) {
        cohortAccessService.requireSystemAdmin(globalRole);
        CommunityPost post = findActivePost(postId);
        post.changePinned(command.pinned());
        return CommunityPostDetail.from(
                post,
                toMetadata(attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(post.getId()))
        );
    }

    private CommunityPost findActivePost(Long postId) {
        return communityPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private void validateCreatePermission(
            UUID userId,
            GlobalRole globalRole,
            CreateCommunityPostCommand command
    ) {
        if (command.type() == null || command.scope() == null) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_REQUEST);
        }

        if (command.type() == CommunityPostType.FREE) {
            requireCohortScoped(command.scope(), command.cohortId());
            requireActiveCohortMember(userId, command.cohortId());
            return;
        }

        if (command.type() == CommunityPostType.NOTICE) {
            if (globalRole == GlobalRole.SYSTEM_ADMIN) {
                validateNoticeScope(command.scope(), command.cohortId());
                return;
            }
            requireCohortScoped(command.scope(), command.cohortId());
            requireNoticeWriter(userId, command.cohortId());
            return;
        }

        throw new BusinessException(CommunityErrorCode.INVALID_POST_REQUEST);
    }

    private void validateManagePermission(UUID userId, GlobalRole globalRole, CommunityPost post) {
        if (globalRole == GlobalRole.SYSTEM_ADMIN && post.isNotice()) {
            return;
        }
        if (post.isFree() && post.getAuthorUserId().equals(userId)) {
            return;
        }
        if (post.isNotice() && post.isCohortScoped()) {
            requireNoticeWriter(userId, post.getCohortId());
            return;
        }
        throw new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED);
    }

    private void validateNoticeScope(CommunityPostScope scope, Long cohortId) {
        if (scope == CommunityPostScope.GLOBAL && cohortId == null) {
            return;
        }
        requireCohortScoped(scope, cohortId);
        requireExistingCohort(cohortId);
    }

    private void requireCohortScoped(CommunityPostScope scope, Long cohortId) {
        if (scope != CommunityPostScope.COHORT || cohortId == null) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_REQUEST);
        }
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

    private void requireExistingCohort(Long cohortId) {
        if (!cohortRepository.existsById(cohortId)) {
            throw new BusinessException(CohortErrorCode.COHORT_NOT_FOUND);
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
