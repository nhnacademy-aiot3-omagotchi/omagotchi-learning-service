package site.omagotchi.learningservice.community.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommunityPostController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@DisplayName("커뮤니티 게시글 조회 API")
class CommunityPostControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityPostQueryService communityPostQueryService;

    @Test
    @DisplayName("목록 조회 요청을 현재 사용자와 필터 조건으로 서비스에 위임한다")
    void getsPosts() throws Exception {
        given(communityPostQueryService.getPosts(
                USER_ID,
                1,
                10,
                CommunityPostType.NOTICE,
                "학습"
        )).willReturn(new CommunityPostPage(List.of(
                new CommunityPostListItem(
                        1L,
                        CommunityPostType.NOTICE,
                        "공지",
                        AUTHOR_ID,
                        CommunityPostScope.GLOBAL,
                        null,
                        true,
                        Instant.parse("2026-08-08T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z")
                )
        ), 1, 10, 11, 2));

        mockMvc.perform(get("/api/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("page", "1")
                        .param("size", "10")
                        .param("type", "NOTICE")
                        .param("search", "학습"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postId").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NOTICE"))
                .andExpect(jsonPath("$.items[0].pinned").value(true))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(communityPostQueryService).getPosts(
                USER_ID,
                1,
                10,
                CommunityPostType.NOTICE,
                "학습"
        );
    }

    @Test
    @DisplayName("상세 조회 요청을 현재 사용자로 서비스에 위임한다")
    void getsPost() throws Exception {
        given(communityPostQueryService.getPost(USER_ID, 1L))
                .willReturn(new CommunityPostDetail(
                        1L,
                        CommunityPostType.FREE,
                        "자유글",
                        "내용",
                        AUTHOR_ID,
                        CommunityPostScope.GLOBAL,
                        null,
                        false,
                        Instant.parse("2026-08-08T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z")
                ));

        mockMvc.perform(get("/api/community/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"))
                .andExpect(jsonPath("$.title").value("자유글"))
                .andExpect(jsonPath("$.content").value("내용"));

        verify(communityPostQueryService).getPost(USER_ID, 1L);
    }
}
