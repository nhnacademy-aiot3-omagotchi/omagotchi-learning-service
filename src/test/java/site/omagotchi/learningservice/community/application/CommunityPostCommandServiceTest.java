package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.infrastructure.CommunityAttachmentProperties;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostAttachmentRepository;
import site.omagotchi.learningservice.community.infrastructure.CommunityPostJpaRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private CommunityAttachmentStorage attachmentStorage;

    @Mock
    private CommunityAuthorNames communityAuthorNames;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private final CommunityAttachmentProperties attachmentProperties = new CommunityAttachmentProperties(
            "community-attachments",
            DataSize.ofMegabytes(5),
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
                attachmentStorage,
                attachmentProperties,
                communityAuthorNames,
                clock
        );
    }

    @Test
    @DisplayName("ACTIVE 기수 멤버는 자유글을 생성한다")
    void createsFreePostForActiveMember() {
        givenActiveMember(true);
        givenSavedPostGetsId(1L);
        given(attachmentRepository.saveAllAndFlush(List.of())).willReturn(List.of());

        var result = communityPostCommandService.create(
                USER_ID,
                COHORT_ID,
                new CreateCommunityPostCommand(CommunityPostType.FREE, "  자유글  ", "  내용  ")
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
    @DisplayName("MENTOR·MANAGER는 공지를 생성한다")
    void createsNoticeForNoticeWriter() {
        givenNoticeWriter(true);
        givenSavedPostGetsId(1L);
        given(attachmentRepository.saveAllAndFlush(List.of())).willReturn(List.of());

        var result = communityPostCommandService.create(
                USER_ID,
                COHORT_ID,
                new CreateCommunityPostCommand(CommunityPostType.NOTICE, "공지", "내용")
        );

        assertAll(
                () -> assertEquals(CommunityPostType.NOTICE, result.type()),
                () -> assertEquals(COHORT_ID, result.cohortId())
        );
    }

    @Test
    @DisplayName("STUDENT는 공지를 생성할 수 없다")
    void rejectsNoticeFromStudent() {
        givenNoticeWriter(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        COHORT_ID,
                        new CreateCommunityPostCommand(CommunityPostType.NOTICE, "공지", "내용")
                )
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    @DisplayName("기수 소속이 아니면 자유글을 생성할 수 없다")
    void rejectsFreePostFromNonMember() {
        givenActiveMember(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        COHORT_ID,
                        new CreateCommunityPostCommand(CommunityPostType.FREE, "자유글", "내용")
                )
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    @DisplayName("첨부파일 metadata 저장 실패 시 저장된 파일을 정리한다")
    void cleansUpStoredAttachmentWhenMetadataPersistenceFails() {
        CommunityAttachmentFile attachmentFile = new CommunityAttachmentFile(
                "image.png",
                "image/png",
                1,
                0,
                () -> new ByteArrayInputStream(new byte[]{1})
        );
        StoredCommunityAttachment storedAttachment = new StoredCommunityAttachment(
                "2026/08/08/file.png",
                "image.png",
                "image/png",
                1L,
                0
        );
        givenActiveMember(true);
        givenSavedPostGetsId(1L);
        given(attachmentStorage.store(attachmentFile)).willReturn(storedAttachment);
        given(attachmentRepository.saveAllAndFlush(any())).willThrow(new RuntimeException("metadata failed"));

        assertThrows(
                RuntimeException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        COHORT_ID,
                        new CreateCommunityPostCommand(
                                CommunityPostType.FREE,
                                "자유글",
                                "내용",
                                List.of(attachmentFile)
                        )
                )
        );

        verify(attachmentStorage).delete("2026/08/08/file.png");
    }

    @Test
    @DisplayName("첨부파일 개수 제한을 초과하면 저장하지 않는다")
    void rejectsTooManyAttachments() {
        List<CommunityAttachmentFile> attachments = IntStream.range(0, 6)
                .mapToObj(index -> new CommunityAttachmentFile(
                        "image" + index + ".png",
                        "image/png",
                        1,
                        index,
                        () -> new ByteArrayInputStream(new byte[]{1})
                ))
                .toList();
        givenActiveMember(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.create(
                        USER_ID,
                        COHORT_ID,
                        new CreateCommunityPostCommand(
                                CommunityPostType.FREE,
                                "자유글",
                                "내용",
                                attachments
                        )
                )
        );

        assertSame(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
        verify(attachmentStorage, never()).store(any());
    }

    @Test
    @DisplayName("자유글 작성자는 제목과 내용을 수정한다")
    void updatesOwnFreePost() {
        givenFoundPost(post(1L, USER_ID, CommunityPostType.FREE));
        givenActiveMember(true);

        var result = communityPostCommandService.update(
                USER_ID,
                COHORT_ID,
                1L,
                new UpdateCommunityPostCommand("수정", "수정 내용")
        );

        assertAll(
                () -> assertEquals("수정", result.title()),
                () -> assertEquals("수정 내용", result.content())
        );
    }

    @Test
    @DisplayName("다른 사용자의 자유글은 삭제할 수 없다")
    void rejectsDeletingOthersFreePost() {
        givenFoundPost(post(1L, OTHER_USER_ID, CommunityPostType.FREE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.delete(USER_ID, COHORT_ID, 1L)
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    @DisplayName("공지는 작성자가 아니어도 MENTOR·MANAGER가 수정한다")
    void allowsNoticeWriterToUpdateOthersNotice() {
        givenFoundPost(post(1L, OTHER_USER_ID, CommunityPostType.NOTICE));
        givenNoticeWriter(true);
        given(attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(1L)).willReturn(List.of());

        var result = communityPostCommandService.update(
                USER_ID,
                COHORT_ID,
                1L,
                new UpdateCommunityPostCommand("수정 공지", "수정 내용")
        );

        assertEquals("수정 공지", result.title());
    }

    @Test
    @DisplayName("다른 기수의 게시글 식별자는 찾을 수 없다")
    void rejectsPostOfAnotherCohort() {
        given(communityPostRepository.findByIdAndCohortIdAndDeletedAtIsNull(1L, COHORT_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.delete(USER_ID, COHORT_ID, 1L)
        );

        assertSame(CommunityErrorCode.POST_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("MENTOR·MANAGER가 게시글 고정 상태를 변경한다")
    void pinsPostForNoticeWriter() {
        givenNoticeWriter(true);
        givenFoundPost(post(1L, OTHER_USER_ID, CommunityPostType.NOTICE));
        given(attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(1L)).willReturn(List.of());

        var result = communityPostCommandService.pin(
                USER_ID,
                COHORT_ID,
                1L,
                new PinCommunityPostCommand(true)
        );

        assertTrue(result.pinned());
    }

    @Test
    @DisplayName("공지 작성 권한이 없으면 고정할 수 없다")
    void rejectsPinFromStudent() {
        givenNoticeWriter(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostCommandService.pin(
                        USER_ID,
                        COHORT_ID,
                        1L,
                        new PinCommunityPostCommand(true)
                )
        );

        assertSame(CommunityErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    @DisplayName("삭제는 deletedAt을 기록한다")
    void softDeletesPost() {
        CommunityPost post = post(1L, USER_ID, CommunityPostType.FREE);
        givenFoundPost(post);
        givenActiveMember(true);

        communityPostCommandService.delete(USER_ID, COHORT_ID, 1L);

        assertNotNull(post.getDeletedAt());
    }

    private void givenActiveMember(boolean active) {
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndStatusIn(
                eq(COHORT_ID),
                eq(USER_ID),
                org.mockito.ArgumentMatchers.<Collection<CohortMembershipStatus>>any()
        )).willReturn(active);
    }

    private void givenNoticeWriter(boolean noticeWriter) {
        given(cohortMembershipRepository.existsByCohortIdAndUserIdAndRoleInAndStatus(
                eq(COHORT_ID),
                eq(USER_ID),
                org.mockito.ArgumentMatchers.<Collection<CohortMembershipRole>>any(),
                eq(CohortMembershipStatus.ACTIVE)
        )).willReturn(noticeWriter);
    }

    private void givenFoundPost(CommunityPost post) {
        given(communityPostRepository.findByIdAndCohortIdAndDeletedAtIsNull(post.getId(), COHORT_ID))
                .willReturn(Optional.of(post));
    }

    private void givenSavedPostGetsId(Long postId) {
        given(communityPostRepository.saveAndFlush(any(CommunityPost.class))).willAnswer(invocation -> {
            CommunityPost post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", postId);
            return post;
        });
    }

    private CommunityPost post(Long postId, UUID authorUserId, CommunityPostType type) {
        CommunityPost post = CommunityPost.create(type, "제목", "내용", authorUserId, COHORT_ID);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }
}
