package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("커뮤니티 게시글 조회 서비스")
@ExtendWith(MockitoExtension.class)
class CommunityPostQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private CommunityPostQueryPort communityPostQueryPort;

    @Mock
    private CommunityAttachmentStorage communityAttachmentStorage;

    @InjectMocks
    private CommunityPostQueryService communityPostQueryService;

    @Test
    @DisplayName("페이지 기본값과 검색어 trim을 적용한다")
    void appliesDefaultPaginationAndNormalizedSearch() {
        given(communityPostQueryPort.findVisiblePosts(org.mockito.ArgumentMatchers.any()))
                .willReturn(new CommunityPostPage(List.of(), 0, 20, 0, 0));

        communityPostQueryService.getPosts(USER_ID, null, null, null, "  학습  ");

        ArgumentCaptor<CommunityPostSearchCondition> captor =
                ArgumentCaptor.forClass(CommunityPostSearchCondition.class);
        verify(communityPostQueryPort).findVisiblePosts(captor.capture());
        CommunityPostSearchCondition condition = captor.getValue();
        assertAll(
                () -> assertEquals(USER_ID, condition.userId()),
                () -> assertEquals(0, condition.page()),
                () -> assertEquals(20, condition.size()),
                () -> assertEquals("학습", condition.search())
        );
    }

    @Test
    @DisplayName("잘못된 페이지 요청을 거절한다")
    void rejectsInvalidPageRequest() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostQueryService.getPosts(USER_ID, -1, 20, null, null)
        );

        assertSame(CommunityErrorCode.INVALID_PAGE_REQUEST, exception.getErrorCode());
        verifyNoInteractions(communityPostQueryPort);
    }

    @Test
    @DisplayName("보이는 게시글에 소속된 첨부파일만 다운로드한다")
    void downloadsAttachmentOnlyAfterPostVisibilityAndOwnershipCheck() {
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        given(communityPostQueryPort.findVisiblePost(USER_ID, 10L)).willReturn(java.util.Optional.of(postDetail()));
        given(communityAttachmentStorage.load("2026/08/21/file.png")).willReturn(resource);

        var result = communityPostQueryService.downloadAttachment(USER_ID, 10L, 20L);

        assertAll(
                () -> assertEquals("화면.png", result.originalFileName()),
                () -> assertEquals("image/png", result.contentType()),
                () -> assertEquals(3L, result.sizeBytes()),
                () -> assertSame(resource, result.resource())
        );
        verify(communityPostQueryPort).findVisiblePost(USER_ID, 10L);
        verify(communityAttachmentStorage).load("2026/08/21/file.png");
    }

    @Test
    @DisplayName("다른 게시글의 첨부파일 식별자는 거절한다")
    void rejectsAttachmentThatDoesNotBelongToVisiblePost() {
        given(communityPostQueryPort.findVisiblePost(USER_ID, 10L)).willReturn(java.util.Optional.of(postDetail()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> communityPostQueryService.downloadAttachment(USER_ID, 10L, 999L)
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
                CommunityPostScope.COHORT,
                1L,
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
