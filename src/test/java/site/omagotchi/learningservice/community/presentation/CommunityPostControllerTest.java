package site.omagotchi.learningservice.community.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestPartFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.application.CommunityPostCommandService;
import site.omagotchi.learningservice.community.application.CommunityPostQueryService;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentDownload;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentPreview;
import site.omagotchi.learningservice.community.application.command.CreateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.PinCommunityPostCommand;
import site.omagotchi.learningservice.community.application.command.UpdateCommunityPostCommand;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = CommunityPostController.class)
@DisplayName("기수 커뮤니티 게시글 API")
@LearningRestDocsTest
class CommunityPostControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Long COHORT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

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
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
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
                .andDo(document(
                        "community/get-posts",
                        pathParameters(cohortId()),
                        queryParameters(
                                parameterWithName("page").description("페이지 번호").optional(),
                                parameterWithName("size").description("페이지 크기").optional(),
                                parameterWithName("type").description("게시글 유형").optional(),
                                parameterWithName("search").description("검색어").optional()),
                        responseFields(pageFields())));

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

        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/community/posts/{post-id}", COHORT_ID, 1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"))
                .andExpect(jsonPath("$.title").value("자유글"))
                .andExpect(jsonPath("$.content").value("내용"))
                .andExpect(jsonPath("$.cohortId").value(10))
                .andExpect(jsonPath("$.authorNickname").value("글쓴이"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.authorUserId").value(AUTHOR_ID.toString()))
                .andDo(document(
                        "community/get-post",
                        pathParameters(cohortId(), postId()),
                        responseFields(detailFields())));

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
                                COHORT_ID,
                                10L,
                                20L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andDo(document(
                        "community/download-attachment",
                        pathParameters(cohortId(), postId(), attachmentId())));

        verify(communityPostQueryService).downloadAttachment(USER_ID, COHORT_ID, 10L, 20L);
    }

    @Test
    @DisplayName("첨부파일 미리보기는 썸네일과 private 캐시 헤더를 반환한다")
    void previewsAttachment() throws Exception {
        given(communityPostQueryService.previewAttachment(USER_ID, COHORT_ID, 10L, 20L))
                .willReturn(new CommunityAttachmentPreview(
                        "image/jpeg",
                        new ByteArrayResource(new byte[]{4, 5, 6})
                ));

        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}/attachments/{attachment-id}/thumbnail",
                                COHORT_ID,
                                10L,
                                20L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(
                        header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(content().bytes(new byte[] {4, 5, 6}))
                .andDo(document(
                        "community/preview-attachment",
                        pathParameters(cohortId(), postId(), attachmentId())));

        verify(communityPostQueryService).previewAttachment(USER_ID, COHORT_ID, 10L, 20L);
    }

    @Test
    @DisplayName("첨부파일 삭제 요청을 서비스에 위임한다")
    void deletesAttachment() throws Exception {
        mockMvc.perform(delete(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}/attachments/{attachment-id}",
                                COHORT_ID,
                                10L,
                                20L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "community/delete-attachment",
                        pathParameters(cohortId(), postId(), attachmentId())));

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
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "type": "FREE",
                          "title": "자유글",
                          "content": "내용"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.type").value("FREE"))
                .andDo(document(
                        "community/create-post",
                        pathParameters(cohortId()),
                        requestFields(
                                fieldWithPath("type").description("게시글 유형"),
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("content").description("내용")),
                        responseFields(detailFields())));

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

        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}",
                                COHORT_ID,
                                1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "title": "수정",
                          "content": "수정 내용"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정"))
                .andExpect(jsonPath("$.content").value("수정 내용"))
                .andDo(document(
                        "community/update-post",
                        pathParameters(cohortId(), postId()),
                        requestFields(
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("content").description("내용")),
                        responseFields(detailFields())));
    }

    @Test
    @DisplayName("게시글 삭제 요청을 서비스에 위임한다")
    void deletesPost() throws Exception {
        mockMvc.perform(delete(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}",
                                COHORT_ID,
                                1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNoContent())
                .andDo(document("community/delete-post", pathParameters(cohortId(), postId())));

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

        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}/pin",
                                COHORT_ID,
                                1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andDo(document(
                        "community/pin-post",
                        pathParameters(cohortId(), postId()),
                        requestFields(fieldWithPath("pinned").description("고정 여부")),
                        responseFields(detailFields())));

        verify(communityPostCommandService).pin(
                eq(USER_ID),
                eq(COHORT_ID),
                eq(1L),
                eq(new PinCommunityPostCommand(true))
        );
    }

    @Test
    @DisplayName("첨부파일 포함 게시글 생성")
    void createsPostWithAttachment() throws Exception {
        // Given: 게시글과 첨부파일 및 생성 응답 준비
        given(
                        communityPostCommandService.create(
                                eq(USER_ID), eq(COHORT_ID), any(CreateCommunityPostCommand.class)))
                .willReturn(detail(1L, CommunityPostType.FREE, "첨부 글", "내용"));
        MockMultipartFile post =
                new MockMultipartFile(
                        "post",
                        "post.json",
                        "application/json",
                        "{\"type\":\"FREE\",\"title\":\"첨부 글\",\"content\":\"내용\"}".getBytes());
        MockMultipartFile attachment =
                new MockMultipartFile("attachments", "note.txt", "text/plain", new byte[] {1, 2});

        // When & Then
        mockMvc.perform(multipart("/api/v1/cohorts/{cohort-id}/community/posts", COHORT_ID)
                        .file(post)
                        .file(attachment)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isCreated())
                .andDo(document(
                        "community/create-post-multipart",
                        pathParameters(cohortId()),
                        requestParts(
                                partWithName("post").description("게시글 JSON 파트"),
                                partWithName("attachments")
                                        .description("첨부 파일 파트")
                                        .optional()),
                        requestPartFields(
                                "post",
                                fieldWithPath("type").description("게시글 유형"),
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("content").description("내용")),
                        responseFields(detailFields())));
    }

    @Test
    @DisplayName("첨부파일 포함 게시글 수정")
    void updatesPostWithAttachment() throws Exception {
        // Given: 게시글과 첨부파일 및 수정 응답 준비
        given(
                        communityPostCommandService.update(
                                eq(USER_ID),
                                eq(COHORT_ID),
                                eq(1L),
                                any(UpdateCommunityPostCommand.class)))
                .willReturn(detail(1L, CommunityPostType.FREE, "수정 첨부", "내용"));
        MockMultipartFile post =
                new MockMultipartFile(
                        "post",
                        "post.json",
                        "application/json",
                        "{\"title\":\"수정 첨부\",\"content\":\"내용\"}".getBytes());
        MockMultipartFile attachment =
                new MockMultipartFile("attachments", "note.txt", "text/plain", new byte[] {3, 4});

        // When & Then
        mockMvc.perform(multipart(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}",
                                COHORT_ID,
                                1L)
                        .file(post)
                        .file(attachment)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .with(
                                request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                }))
                .andExpect(status().isOk())
                .andDo(document(
                        "community/update-post-multipart",
                        pathParameters(cohortId(), postId()),
                        requestParts(
                                partWithName("post").description("게시글 JSON 파트"),
                                partWithName("attachments")
                                        .description("첨부 파일 파트")
                                        .optional()),
                        requestPartFields(
                                "post",
                                fieldWithPath("title").description("제목"),
                                fieldWithPath("content").description("내용")),
                        responseFields(detailFields())));
    }

    private static ParameterDescriptor cohortId() {
        return parameterWithName("cohort-id").description("기수 식별자");
    }

    private static ParameterDescriptor postId() {
        return parameterWithName("post-id").description("게시글 식별자");
    }

    private static ParameterDescriptor attachmentId() {
        return parameterWithName("attachment-id").description("첨부파일 식별자");
    }

    private static FieldDescriptor[] pageFields() {
        return new FieldDescriptor[] {
            fieldWithPath("items").description("게시글 목록"),
            fieldWithPath("items[].postId").description("게시글 식별자"),
            fieldWithPath("items[].type").description("게시글 유형"),
            fieldWithPath("items[].title").description("제목"),
            fieldWithPath("items[].authorUserId").description("작성자 식별자"),
            fieldWithPath("items[].authorNickname").description("작성자 닉네임"),
            fieldWithPath("items[].cohortId").description("기수 식별자"),
            fieldWithPath("items[].pinned").description("고정 여부"),
            fieldWithPath("items[].createdAt").description("생성 시각"),
            fieldWithPath("items[].updatedAt").description("수정 시각"),
            fieldWithPath("items[].attachmentCount").description("첨부파일 수"),
            fieldWithPath("items[].canManage").description("현재 사용자의 관리 가능 여부"),
            fieldWithPath("pinned").description("고정 공지 (없으면 null)"),
            fieldWithPath("pinned.postId").description("고정 게시글 식별자").optional(),
            fieldWithPath("pinned.type").description("고정 게시글 유형").optional(),
            fieldWithPath("pinned.title").description("고정 게시글 제목").optional(),
            fieldWithPath("pinned.authorUserId").description("고정 게시글 작성자").optional(),
            fieldWithPath("pinned.authorNickname").description("고정 게시글 작성자 닉네임").optional(),
            fieldWithPath("pinned.cohortId").description("고정 게시글 기수").optional(),
            fieldWithPath("pinned.pinned").description("고정 여부").optional(),
            fieldWithPath("pinned.createdAt").description("생성 시각").optional(),
            fieldWithPath("pinned.updatedAt").description("수정 시각").optional(),
            fieldWithPath("pinned.attachmentCount").description("첨부파일 수").optional(),
            fieldWithPath("pinned.canManage").description("관리 가능 여부").optional(),
            fieldWithPath("page").description("페이지 정보"),
            fieldWithPath("page.number").description("페이지 번호"),
            fieldWithPath("page.size").description("페이지 크기"),
            fieldWithPath("page.totalElements").description("전체 게시글 수"),
            fieldWithPath("page.totalPages").description("전체 페이지 수")
        };
    }

    private static FieldDescriptor[] detailFields() {
        return new FieldDescriptor[] {
            fieldWithPath("postId").description("게시글 식별자"),
            fieldWithPath("type").description("게시글 유형"),
            fieldWithPath("title").description("제목"),
            fieldWithPath("content").description("내용"),
            fieldWithPath("authorUserId").description("작성자 식별자"),
            fieldWithPath("authorNickname").description("작성자 닉네임"),
            fieldWithPath("cohortId").description("기수 식별자"),
            fieldWithPath("pinned").description("고정 여부"),
            fieldWithPath("createdAt").description("생성 시각"),
            fieldWithPath("updatedAt").description("수정 시각"),
            fieldWithPath("attachments").description("첨부파일 목록"),
            fieldWithPath("attachments[].attachmentId").description("첨부파일 식별자"),
            fieldWithPath("attachments[].originalFileName").description("원본 파일명"),
            fieldWithPath("attachments[].contentType").description("파일 Content-Type"),
            fieldWithPath("attachments[].sizeBytes").description("파일 크기(바이트)"),
            fieldWithPath("attachments[].displayOrder").description("표시 순서"),
            fieldWithPath("canManage").description("현재 사용자의 관리 가능 여부")
        };
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
                List.of(
                        new CommunityAttachmentMetadata(
                                20L, "community/1/20", "note.txt", "text/plain", 2L, 0)),
                true);
    }

    @Test
    @DisplayName("인증 없는 게시글 조회 거절")
    void rejectsMissingAuthentication() throws Exception {
        // Given: 인증 없는 게시글 조회 거절

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/community/posts/{post-id}", COHORT_ID, 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"))
                .andDo(document("community/missing-authentication", responseFields(errorFields())));
    }

    @Test
    @DisplayName("소속 없는 사용자의 게시글 조회 거절")
    void rejectsMissingMembership() throws Exception {
        // Given: 소속 없는 사용자의 게시글 조회 거절
        given(communityPostQueryService.getPost(USER_ID, COHORT_ID, 1L))
                .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/community/posts/{post-id}", COHORT_ID, 1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COHORT_NOT_FOUND"))
                .andDo(document("community/missing-membership", responseFields(errorFields())));
    }

    @Test
    @DisplayName("관리 권한 없는 게시글 삭제 거절")
    void rejectsDeniedCommand() throws Exception {
        // Given: 관리 권한 없는 게시글 삭제 거절
        willThrow(new BusinessException(CommunityErrorCode.POST_ACCESS_DENIED))
                .given(communityPostCommandService)
                .delete(USER_ID, COHORT_ID, 1L);
        // When & Then
        mockMvc.perform(delete(
                                "/api/v1/cohorts/{cohort-id}/community/posts/{post-id}",
                                COHORT_ID,
                                1L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMUNITY_POST_ACCESS_DENIED"))
                .andDo(document("community/access-denied", responseFields(errorFields())));
    }

    private static FieldDescriptor[] errorFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("오류 코드"),
            fieldWithPath("message").description("오류 메시지"),
            fieldWithPath("path").description("요청 경로"),
            fieldWithPath("requestId").optional().description("요청 추적 ID")
        };
    }
}
