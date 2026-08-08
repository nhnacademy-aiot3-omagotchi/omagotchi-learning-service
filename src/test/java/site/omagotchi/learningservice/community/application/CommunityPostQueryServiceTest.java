package site.omagotchi.learningservice.community.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;

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
}
