package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("커뮤니티 게시글 조회 서비스")
@ExtendWith(MockitoExtension.class)
class CommunityPostQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 1L;

    @Mock
    private CommunityPostQueryPort communityPostQueryPort;

    @Mock
    private CommunityAttachmentStorage communityAttachmentStorage;

    @Mock
    private CohortAccessService cohortAccessService;

    @InjectMocks
    private CommunityPostQueryService communityPostQueryService;

    @Test
    @DisplayName("페이지 기본값과 검색어 trim을 적용하고 조회 기수를 조건에 담는다")
    void appliesDefaultPaginationAndNormalizedSearch() {
        given(communityPostQueryPort.findVisiblePosts(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CommunityPostPage(List.of(), 0, 20, 0, 0));

        communityPostQueryService.getPosts(USER_ID, COHORT_ID, null, null, null, "  학습  ");

        ArgumentCaptor<CommunityPostSearchCondition> captor =
                ArgumentCaptor.forClass(CommunityPostSearchCondition.class);
        verify(communityPostQueryPort).findVisiblePosts(captor.capture());
        CommunityPostSearchCondition condition = captor.getValue();
        assertAll(
                () -> assertEquals(COHORT_ID, condition.cohortId()),
                () -> assertEquals(0, condition.page()),
                () -> assertEquals(20, condition.size()),
                () -> assertEquals("학습", condition.search())
        );
    }

    @Test
    @DisplayName("ACTIVE 소속이 아니면 조회하지 않는다")
    void rejectsNonMemberBeforeQuerying() {
        willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND))
                .given(cohortAccessService).requireActiveMembership(COHORT_ID, USER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostQueryService.getPosts(USER_ID, COHORT_ID, 0, 20, null, null)
        );

        assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(communityPostQueryPort);
    }

    @Test
    @DisplayName("잘못된 페이지 요청을 거절한다")
    void rejectsInvalidPageRequest() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostQueryService.getPosts(USER_ID, COHORT_ID, -1, 20, null, null)
        );

        assertSame(CommunityErrorCode.INVALID_PAGE_REQUEST, exception.getErrorCode());
        verifyNoInteractions(communityPostQueryPort);
    }

    @Test
    @DisplayName("보이는 게시글에 소속된 첨부파일만 다운로드한다")
    void downloadsAttachmentOnlyAfterPostVisibilityAndOwnershipCheck() {
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        given(communityPostQueryPort.findVisiblePost(COHORT_ID, 10L))
                .willReturn(Optional.of(postDetail()));
        given(communityAttachmentStorage.load("2026/08/21/file.png")).willReturn(resource);

        var result = communityPostQueryService.downloadAttachment(USER_ID, COHORT_ID, 10L, 20L);

        assertAll(
                () -> assertEquals("화면.png", result.originalFileName()),
                () -> assertEquals("image/png", result.contentType()),
                () -> assertEquals(3L, result.sizeBytes()),
                () -> assertSame(resource, result.resource())
        );
        verify(communityPostQueryPort).findVisiblePost(COHORT_ID, 10L);
        verify(communityAttachmentStorage).load("2026/08/21/file.png");
    }

    @Test
    @DisplayName("다른 게시글의 첨부파일 식별자는 거절한다")
    void rejectsAttachmentThatDoesNotBelongToVisiblePost() {
        given(communityPostQueryPort.findVisiblePost(COHORT_ID, 10L))
                .willReturn(Optional.of(postDetail()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostQueryService.downloadAttachment(USER_ID, COHORT_ID, 10L, 999L)
        );

        assertSame(CommunityErrorCode.ATTACHMENT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(communityAttachmentStorage);
    }

    private CommunityPostDetail postDetail() {
        return new CommunityPostDetail(
                10L,
                CommunityPostType.FREE,
                "제목",
                "내용",
                USER_ID,
                COHORT_ID,
                false,
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                List.of(new CommunityAttachmentMetadata(
                        20L,
                        "2026/08/21/file.png",
                        "화면.png",
                        "image/png",
                        3L,
                        0
                ))
        );
    }
}
