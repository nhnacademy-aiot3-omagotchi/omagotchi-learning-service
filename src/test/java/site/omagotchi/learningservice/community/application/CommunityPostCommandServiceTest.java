package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.infrastructure.CommunityAttachmentProperties;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostAttachmentRepository;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostJpaRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;

@DisplayName("커뮤니티 게시글 명령 서비스")
@ExtendWith(MockitoExtension.class)
class CommunityPostCommandServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Long COHORT_ID = 10L;

    @Mock
    private CommunityPostJpaRepository communityPostRepository;

    @Mock
    private CommunityPostAttachmentRepository attachmentRepository;

    @Mock
    private CohortMembershipRepository cohortMembershipRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CommunityAttachmentStorage attachmentStorage;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private final CommunityAttachmentProperties attachmentProperties = new CommunityAttachmentProperties(
            Path.of("data/community-attachments"),
            org.springframework.util.unit.DataSize.ofMegabytes(5),
            5,
            List.of("jpg", "jpeg", "png", "gif"),
            List.of("image/jpeg", "image/png", "image/gif")
    );

    private CommunityPostCommandService communityPostCommandService;

    @BeforeEach
    void setUp() {
        communityPostCommandService = new CommunityPostCommandService(
                communityPostRepository,
                attachmentRepository,
                cohortMembershipRepository,
                cohortRepository,
                cohortAccessService,
                attachmentStorage,
                attachmentProperties,
                clock
        );
    }

    @Test
    @DisplayName("ACTIVE 기수 멤버는 COHORT FREE 게시글을 생성한다")
    void createsCohortFreePostForActiveMember() {
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndStatusIn(
                eq(COHORT_ID),
                eq(USER_ID),
                org.mockito.ArgumentMatchers.<java.util.Collection<CohortMembershipStatus>>any()
        )).willReturn(true);
        given(communityPostRepository.saveAndFlush(any(CommunityPost.class))).willAnswer(invocation -> {
            CommunityPost post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", 1L);
            return post;
        });
        given(attachmentRepository.saveAllAndFlush(List.of())).willReturn(List.of());

        var result = communityPostCommandService.create(
                USER_ID,
                GlobalRole.USER,
                new CreateCommunityPostCommand(
                        CommunityPostType.FREE,
                        "  자유글  ",
                        "  내용  ",
                        CommunityPostScope.COHORT,
                        COHORT_ID
                )
        );

        assertAll(
                () -> assertEquals(1L, result.postId()),
                () -> assertEquals(CommunityPostType.FREE, result.type()),
                () -> assertEquals("자유글", result.title()),
                () -> assertEquals("내용", result.content()),
                () -> assertEquals(USER_ID, result.authorUserId()),
                () -> assertEquals(COHORT_ID, result.cohortId())
        );
    }

    @Test
    @DisplayName("MENTOR는 ACTIVE 소속 기수에 COHORT NOTICE를 생성한다")
    void createsCohortNoticeForMentor() {
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndRoleInAndStatus(
                COHORT_ID,
                USER_ID,
                java.util.Set.of(CohortMembershipRole.MANAGER, CohortMembershipRole.MENTOR),
                CohortMembershipStatus.ACTIVE
        )).willReturn(true);
        given(communityPostRepository.saveAndFlush(any(CommunityPost.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(attachmentRepository.saveAllAndFlush(List.of())).willReturn(List.of());

        var result = communityPostCommandService.create(
                USER_ID,
                GlobalRole.USER,
                new CreateCommunityPostCommand(
                        CommunityPostType.NOTICE,
                        "공지",
                        "내용",
                        CommunityPostScope.COHORT,
                        COHORT_ID
                )
        );

        assertEquals(CommunityPostType.NOTICE, result.type());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN은 GLOBAL NOTICE를 생성한다")
    void createsGlobalNoticeForSystemAdmin() {
        given(communityPostRepository.saveAndFlush(any(CommunityPost.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(attachmentRepository.saveAllAndFlush(List.of())).willReturn(List.of());

        var result = communityPostCommandService.create(
                USER_ID,
                GlobalRole.SYSTEM_ADMIN,
                new CreateCommunityPostCommand(
                        CommunityPostType.NOTICE,
                        "전체 공지",
                        "내용",
                        CommunityPostScope.GLOBAL,
                        null
                )
        );

        assertAll(
                () -> assertEquals(CommunityPostScope.GLOBAL, result.scope()),
                () -> assertEquals(null, result.cohortId())
        );
        verifyNoInteractions(cohortRepository);
    }

    @Test
    @DisplayName("첨부파일 metadata 저장 실패 시 저장된 파일을 정리한다")
    void cleansUpStoredAttachmentWhenMetadataPersistenceFails() {
        CommunityAttachmentFile attachmentFile = new CommunityAttachmentFile(
                "image.png",
                "image/png",
                1,
                0,
                () -> new java.io.ByteArrayInputStream(new byte[]{1})
        );
        StoredCommunityAttachment storedAttachment = new StoredCommunityAttachment(
                "2026/08/08/file.png",
                "image.png",
                "image/png",
                1L,
                0
        );
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndStatusIn(
                eq(COHORT_ID),
                eq(USER_ID),
                org.mockito.ArgumentMatchers.<java.util.Collection<CohortMembershipStatus>>any()
        )).willReturn(true);
        given(communityPostRepository.saveAndFlush(any(CommunityPost.class))).willAnswer(invocation -> {
            CommunityPost post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", 1L);
            return post;
        });
        given(attachmentStorage.store(attachmentFile)).willReturn(storedAttachment);
        given(attachmentRepository.saveAllAndFlush(any())).willThrow(new RuntimeException("metadata failed"));

        assertThrows(
                RuntimeException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        GlobalRole.USER,
                        new CreateCommunityPostCommand(
                                CommunityPostType.FREE,
                                "자유글",
                                "내용",
                                CommunityPostScope.COHORT,
                                COHORT_ID,
                                List.of(attachmentFile)
                        )
                )
        );

        verify(attachmentStorage).delete("2026/08/08/file.png");
    }

    @Test
    @DisplayName("첨부파일 개수 제한을 초과하면 저장하지 않는다")
    void rejectsTooManyAttachments() {
        List<CommunityAttachmentFile> attachments = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> new CommunityAttachmentFile(
                        "image" + index + ".png",
                        "image/png",
                        1,
                        index,
                        () -> new java.io.ByteArrayInputStream(new byte[]{1})
                ))
                .toList();
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndStatusIn(
                eq(COHORT_ID),
                eq(USER_ID),
                org.mockito.ArgumentMatchers.<java.util.Collection<CohortMembershipStatus>>any()
        )).willReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        GlobalRole.USER,
                        new CreateCommunityPostCommand(
                                CommunityPostType.FREE,
                                "자유글",
                                "내용",
                                CommunityPostScope.COHORT,
                                COHORT_ID,
                                attachments
                        )
                )
        );

        assertSame(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
        verify(attachmentStorage, never()).store(any());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN의 COHORT NOTICE는 기수 존재를 검증한다")
    void validatesCohortForSystemAdminCohortNotice() {
        given(cohortRepository.existsById(COHORT_ID)).willReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        GlobalRole.SYSTEM_ADMIN,
                        new CreateCommunityPostCommand(
                                CommunityPostType.NOTICE,
                                "기수 공지",
                                "내용",
                                CommunityPostScope.COHORT,
                                COHORT_ID
                        )
                )
        );

        assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    @DisplayName("STUDENT는 NOTICE를 생성할 수 없다")
    void rejectsNoticeFromStudent() {
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndRoleInAndStatus(
                COHORT_ID,
                USER_ID,
                java.util.Set.of(CohortMembershipRole.MANAGER, CohortMembershipRole.MENTOR),
                CohortMembershipStatus.ACTIVE
        )).willReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        GlobalRole.USER,
                        new CreateCommunityPostCommand(
                                CommunityPostType.NOTICE,
                                "공지",
                                "내용",
                                CommunityPostScope.COHORT,
                                COHORT_ID
                        )
                )
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    @DisplayName("FREE 게시글 작성자는 제목과 내용을 수정한다")
    void updatesOwnFreePost() {
        CommunityPost post = post(1L, USER_ID, CommunityPostType.FREE, CommunityPostScope.COHORT, COHORT_ID);
        given(communityPostRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

        var result = communityPostCommandService.update(
                USER_ID,
                GlobalRole.USER,
                1L,
                new UpdateCommunityPostCommand("수정", "수정 내용")
        );

        assertAll(
                () -> assertEquals("수정", result.title()),
                () -> assertEquals("수정 내용", result.content())
        );
    }

    @Test
    @DisplayName("다른 사용자의 FREE 게시글은 삭제할 수 없다")
    void rejectsDeletingOthersFreePost() {
        CommunityPost post = post(1L, OTHER_USER_ID, CommunityPostType.FREE, CommunityPostScope.COHORT, COHORT_ID);
        given(communityPostRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.delete(USER_ID, GlobalRole.USER, 1L)
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    @DisplayName("SYSTEM_ADMIN만 게시글 고정 상태를 변경한다")
    void pinsPostForSystemAdminOnly() {
        CommunityPost post = post(1L, OTHER_USER_ID, CommunityPostType.FREE, CommunityPostScope.COHORT, COHORT_ID);
        given(communityPostRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

        var result = communityPostCommandService.pin(
                USER_ID,
                GlobalRole.SYSTEM_ADMIN,
                1L,
                new PinCommunityPostCommand(true)
        );

        assertEquals(true, result.pinned());
        verify(cohortAccessService).requireSystemAdmin(GlobalRole.SYSTEM_ADMIN);
    }

    @Test
    @DisplayName("삭제는 deletedAt을 기록한다")
    void softDeletesPost() {
        CommunityPost post = post(1L, USER_ID, CommunityPostType.FREE, CommunityPostScope.COHORT, COHORT_ID);
        given(communityPostRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(post));

        communityPostCommandService.delete(USER_ID, GlobalRole.USER, 1L);

        assertNotNull(post.getDeletedAt());
    }

    private CommunityPost post(
            Long postId,
            UUID authorUserId,
            CommunityPostType type,
            CommunityPostScope scope,
            Long cohortId
    ) {
        CommunityPost post = CommunityPost.create(
                type,
                "제목",
                "내용",
                authorUserId,
                scope,
                cohortId
        );
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }
}
