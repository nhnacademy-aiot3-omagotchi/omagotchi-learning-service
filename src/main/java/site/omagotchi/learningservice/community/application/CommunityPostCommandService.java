package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostJpaRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostCommandService {

    private static final Set<CohortMembershipRole> NOTICE_WRITER_ROLES = Set.of(
            CohortMembershipRole.MANAGER,
            CohortMembershipRole.MENTOR
    );

    private final CommunityPostJpaRepository communityPostRepository;
    private final CohortMembershipRepository cohortMembershipRepository;
    private final CohortRepository cohortRepository;
    private final CohortAccessService cohortAccessService;
    private final Clock clock;

    @Transactional
    public CommunityPostDetail create(
            UUID userId,
            GlobalRole globalRole,
            CreateCommunityPostCommand command
    ) {
        validateCreatePermission(userId, globalRole, command);
        CommunityPost post = CommunityPost.create(
                command.type(),
                command.title(),
                command.content(),
                userId,
                command.scope(),
                command.cohortId()
        );
        return CommunityPostDetail.from(communityPostRepository.save(post));
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
        return CommunityPostDetail.from(post);
    }

    @Transactional
    public void delete(UUID userId, GlobalRole globalRole, Long postId) {
        CommunityPost post = findActivePost(postId);
        validateManagePermission(userId, globalRole, post);
        post.delete(clock.instant());
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
        return CommunityPostDetail.from(post);
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
}
