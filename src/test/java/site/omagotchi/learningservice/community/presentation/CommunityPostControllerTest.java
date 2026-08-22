package site.omagotchi.learningservice.community.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.community.application.CommunityPostCommandService;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentDownload;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
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
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("커뮤니티 게시글 조회 API")
class CommunityPostControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityPostQueryService communityPostQueryService;

    @MockitoBean
    private CommunityPostCommandService communityPostCommandService;

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

        mockMvc.perform(get("/api/v1/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("page", "1")
                        .param("size", "10")
                        .param("type", "NOTICE")
                        .param("search", "학습"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postId").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NOTICE"))
                .andExpect(jsonPath("$.items[0].pinned").value(true))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(11))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.size").doesNotExist())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist())
                .andDo(document("community/get-posts"));

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

        mockMvc.perform(get("/api/v1/community/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"))
                .andExpect(jsonPath("$.title").value("자유글"))
                .andExpect(jsonPath("$.content").value("내용"));

        verify(communityPostQueryService).getPost(USER_ID, 1L);
    }

    @Test
    @DisplayName("첨부파일 다운로드는 안전한 응답 헤더와 파일 본문을 반환한다")
    void downloadsAttachment() throws Exception {
        given(communityPostQueryService.downloadAttachment(USER_ID, 10L, 20L))
                .willReturn(new CommunityAttachmentDownload(
                        "화면.png",
                        "image/png",
                        3L,
                        new ByteArrayResource(new byte[]{1, 2, 3})
                ));

        mockMvc.perform(get("/api/v1/community/posts/{postId}/attachments/{attachmentId}", 10L, 20L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(new byte[]{1, 2, 3}));

        verify(communityPostQueryService).downloadAttachment(USER_ID, 10L, 20L);
    }

    @Test
    @DisplayName("게시글 생성 요청은 JWT 사용자와 역할을 사용한다")
    void createsPostWithJwtUser() throws Exception {
        given(communityPostCommandService.create(
                eq(USER_ID),
                eq(site.omagotchi.learningservice.global.auth.GlobalRole.USER),
                eq(new CreateCommunityPostCommand(
                        CommunityPostType.FREE,
                        "자유글",
                        "내용",
                        CommunityPostScope.COHORT,
                        10L
                ))
        )).willReturn(detail(1L, CommunityPostType.FREE, "자유글", "내용"));

        mockMvc.perform(post("/api/v1/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000099")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "title": "자유글",
                                  "content": "내용",
                                  "scope": "COHORT",
                                  "cohortId": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"));

        verify(communityPostCommandService).create(
                eq(USER_ID),
                eq(site.omagotchi.learningservice.global.auth.GlobalRole.USER),
                eq(new CreateCommunityPostCommand(
                        CommunityPostType.FREE,
                        "자유글",
                        "내용",
                        CommunityPostScope.COHORT,
                        10L
                ))
        );
    }

    @Test
    @DisplayName("게시글 수정 요청을 서비스에 위임한다")
    void updatesPost() throws Exception {
        given(communityPostCommandService.update(
                eq(USER_ID),
                eq(site.omagotchi.learningservice.global.auth.GlobalRole.USER),
                eq(1L),
                eq(new UpdateCommunityPostCommand("수정", "수정 내용"))
        )).willReturn(detail(1L, CommunityPostType.FREE, "수정", "수정 내용"));

        mockMvc.perform(patch("/api/v1/community/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정",
                                  "content": "수정 내용"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정"))
                .andExpect(jsonPath("$.content").value("수정 내용"));
    }

    @Test
    @DisplayName("게시글 삭제 요청을 서비스에 위임한다")
    void deletesPost() throws Exception {
        mockMvc.perform(delete("/api/v1/community/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent());

        verify(communityPostCommandService).delete(
                USER_ID,
                site.omagotchi.learningservice.global.auth.GlobalRole.USER,
                1L
        );
    }

    @Test
    @DisplayName("게시글 고정 요청을 서비스에 위임한다")
    void pinsPost() throws Exception {
        String adminToken = TestJwtKeyConfig.issue("SYSTEM_ADMIN");
        given(communityPostCommandService.pin(
                eq(USER_ID),
                eq(site.omagotchi.learningservice.global.auth.GlobalRole.SYSTEM_ADMIN),
                eq(1L),
                eq(new PinCommunityPostCommand(true))
        )).willReturn(detail(1L, CommunityPostType.NOTICE, "공지", "내용"));

        mockMvc.perform(patch("/api/v1/community/posts/1/pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1));
    }

    private CommunityPostDetail detail(
            Long postId,
            CommunityPostType type,
            String title,
            String content
    ) {
        return new CommunityPostDetail(
                postId,
                type,
                title,
                content,
                AUTHOR_ID,
                CommunityPostScope.GLOBAL,
                null,
                false,
                Instant.parse("2026-08-08T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
