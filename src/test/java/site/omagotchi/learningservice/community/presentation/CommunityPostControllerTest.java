package site.omagotchi.learningservice.community.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@DisplayName("기수 커뮤니티 게시글 API")
class CommunityPostControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Long COHORT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityPostQueryService communityPostQueryService;

    @MockitoBean
    private CommunityPostCommandService communityPostCommandService;

    @Test
    @DisplayName("목록 조회 요청을 현재 사용자와 경로 기수, 필터 조건으로 서비스에 위임한다")
    void getsPosts() throws Exception {
        given(communityPostQueryService.getPosts(
                USER_ID,
                COHORT_ID,
                1,
                10,
                CommunityPostType.NOTICE,
                "학습"
        )).willReturn(new CommunityPostPage(List.of(
                new CommunityPostListItem(
                        2L,
                        CommunityPostType.NOTICE,
                        "일반 공지",
                        AUTHOR_ID,
                        "글쓴이",
                        COHORT_ID,
                        false,
                        Instant.parse("2026-08-08T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z"),
                        0L,
                        true
                )
        ), new CommunityPostListItem(
                1L,
                CommunityPostType.NOTICE,
                "고정 공지",
                AUTHOR_ID,
                "기수장",
                COHORT_ID,
                true,
                Instant.parse("2026-08-09T00:00:00Z"),
                Instant.parse("2026-08-09T00:00:00Z"),
                0L,
                false
        ), 1, 10, 11, 2));

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/community/posts", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .param("page", "1")
                        .param("size", "10")
                        .param("type", "NOTICE")
                        .param("search", "학습"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postId").value(2))
                .andExpect(jsonPath("$.items[0].type").value("NOTICE"))
                .andExpect(jsonPath("$.items[0].cohortId").value(10))
                .andExpect(jsonPath("$.items[0].authorNickname").value("글쓴이"))
                .andExpect(jsonPath("$.items[0].canManage").value(true))
                .andExpect(jsonPath("$.items[0].authorUserId").value(AUTHOR_ID.toString()))
                .andExpect(jsonPath("$.items[0].pinned").value(false))
                .andExpect(jsonPath("$.pinned.postId").value(1))
                .andExpect(jsonPath("$.pinned.pinned").value(true))
                .andExpect(jsonPath("$.pinned.canManage").value(false))
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
                COHORT_ID,
                1,
                10,
                CommunityPostType.NOTICE,
                "학습"
        );
    }

    @Test
    @DisplayName("상세 조회 요청을 현재 사용자와 경로 기수로 서비스에 위임한다")
    void getsPost() throws Exception {
        given(communityPostQueryService.getPost(USER_ID, COHORT_ID, 1L))
                .willReturn(detail(1L, CommunityPostType.FREE, "자유글", "내용"));

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/community/posts/1", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"))
                .andExpect(jsonPath("$.title").value("자유글"))
                .andExpect(jsonPath("$.content").value("내용"))
                .andExpect(jsonPath("$.cohortId").value(10))
                .andExpect(jsonPath("$.authorNickname").value("글쓴이"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.authorUserId").value(AUTHOR_ID.toString()));

        verify(communityPostQueryService).getPost(USER_ID, COHORT_ID, 1L);
    }

    @Test
    @DisplayName("첨부파일 다운로드는 안전한 응답 헤더와 파일 본문을 반환한다")
    void downloadsAttachment() throws Exception {
        given(communityPostQueryService.downloadAttachment(USER_ID, COHORT_ID, 10L, 20L))
                .willReturn(new CommunityAttachmentDownload(
                        "화면.png",
                        "image/png",
                        3L,
                        new ByteArrayResource(new byte[]{1, 2, 3})
                ));

        mockMvc.perform(get(
                        "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}/attachments/{attachment-id}",
                        COHORT_ID, 10L, 20L
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        verify(communityPostQueryService).downloadAttachment(USER_ID, COHORT_ID, 10L, 20L);
    }

    @Test
    @DisplayName("첨부파일 삭제 요청을 서비스에 위임한다")
    void deletesAttachment() throws Exception {
        mockMvc.perform(delete(
                        "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}/attachments/{attachment-id}",
                        COHORT_ID, 10L, 20L
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent());

        verify(communityPostCommandService).deleteAttachment(USER_ID, COHORT_ID, 10L, 20L);
    }

    @Test
    @DisplayName("게시글 생성은 소속 기수를 경로에서 받고 본문 지정은 받지 않는다")
    void createsPostWithCohortFromPath() throws Exception {
        given(communityPostCommandService.create(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(new CreateCommunityPostCommand(CommunityPostType.FREE, "자유글", "내용"))
        )).willReturn(detail(1L, CommunityPostType.FREE, "자유글", "내용"));

        mockMvc.perform(post("/api/v1/cohorts/{cohort-id}/community/posts", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "FREE",
                                  "title": "자유글",
                                  "content": "내용"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"));

        verify(communityPostCommandService).create(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(new CreateCommunityPostCommand(CommunityPostType.FREE, "자유글", "내용"))
        );
    }

    @Test
    @DisplayName("게시글 수정 요청을 서비스에 위임한다")
    void updatesPost() throws Exception {
        given(communityPostCommandService.update(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(1L),
                eq(new UpdateCommunityPostCommand("수정", "수정 내용"))
        )).willReturn(detail(1L, CommunityPostType.FREE, "수정", "수정 내용"));

        mockMvc.perform(patch("/api/v1/cohorts/{cohort-id}/community/posts/1", COHORT_ID)
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
        mockMvc.perform(delete("/api/v1/cohorts/{cohort-id}/community/posts/1", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent());

        verify(communityPostCommandService).delete(USER_ID, COHORT_ID, 1L);
    }

    @Test
    @DisplayName("게시글 고정 요청을 서비스에 위임한다")
    void pinsPost() throws Exception {
        given(communityPostCommandService.pin(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(1L),
                eq(new PinCommunityPostCommand(true))
        )).willReturn(detail(1L, CommunityPostType.NOTICE, "공지", "내용"));

        mockMvc.perform(patch("/api/v1/cohorts/{cohort-id}/community/posts/1/pin", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1));

        verify(communityPostCommandService).pin(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(1L),
                eq(new PinCommunityPostCommand(true))
        );
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
                "글쓴이",
                COHORT_ID,
                false,
                Instant.parse("2026-08-08T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z"),
                List.of(),
                true
        );
    }
}
