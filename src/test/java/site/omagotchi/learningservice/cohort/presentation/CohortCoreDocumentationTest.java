package site.omagotchi.learningservice.cohort.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerLookupService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.UserAccessContextService;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.application.result.CohortAccessSummary;
import site.omagotchi.learningservice.cohort.application.result.CohortAdminSummaryResult;
import site.omagotchi.learningservice.cohort.application.result.CohortResponse;
import site.omagotchi.learningservice.cohort.application.result.UserAccessContextResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessType;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = CohortController.class)
@LearningRestDocsTest
class CohortCoreDocumentationTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final String AUTHORIZATION = "Bearer " + TestJwtKeyConfig.issue("SYSTEM_ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private CohortService cohortService;

    @MockitoBean
    private JoinCodeService joinCodeService;

    @MockitoBean
    private CohortMembershipService membershipService;

    @MockitoBean
    private CohortManagerService managerService;

    @MockitoBean
    private CohortManagerLookupService managerLookupService;

    @MockitoBean
    private CohortAttendancePolicyService attendancePolicyService;

    @MockitoBean
    private UserAccessContextService userAccessContextService;

    @Test
    @DisplayName("기수 생성")
    void createsCohort() throws Exception {
        // Given: 기수 생성에 필요한 요청과 서비스 응답을 준비한다.
        given(
                        cohortService.create(
                                eq(
                                        new CreateCohortCommand(
                                                "테스트 기수",
                                                "문서용 기수",
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 12, 31))),
                                eq(USER_ID),
                                eq(GlobalRole.SYSTEM_ADMIN)))
                .willReturn(cohortResponse(CohortStatus.PREPARING));

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"name":"테스트 기수","description":"문서용 기수","startDate":"2026-01-01","endDate":"2026-12-31"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andDo(document(
                        "cohort-core/create",
                        requestFields(cohortRequestFields()),
                        responseFields(cohortResponseFields())));
    }

    @Test
    @DisplayName("기수 목록 조회")
    void getsCohorts() throws Exception {
        // Given: 기수 목록 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(cohortService.getCohorts())
                .willReturn(List.of(cohortResponse(CohortStatus.PREPARING)));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andDo(document("cohort-core/get-all", responseFields(cohortListFields())));
    }

    @Test
    @DisplayName("관리자 기수 요약 조회")
    void getsAdminSummary() throws Exception {
        // Given: 관리자 기수 요약 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(cohortService.getAdminSummaries(GlobalRole.SYSTEM_ADMIN))
                .willReturn(
                        List.of(
                                new CohortAdminSummaryResult(
                                        COHORT_ID,
                                        "테스트 기수",
                                        "문서용 기수",
                                        LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 12, 31),
                                        CohortStatus.ACTIVE,
                                        12L,
                                        List.of(USER_ID))));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/admin-summary")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberCount").value(12))
                .andDo(document(
                        "cohort-core/get-admin-summary",
                        responseFields(adminSummaryFields())));
    }

    @Test
    @DisplayName("내 접근 권한 조회")
    void getsAccessContext() throws Exception {
        // Given: 내 접근 권한 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(userAccessContextService.getContext(USER_ID, GlobalRole.USER))
                .willReturn(
                        new UserAccessContextResult(
                                GlobalRole.USER,
                                UserAccessType.COHORT_MANAGER,
                                List.of(
                                        new CohortAccessSummary(
                                                COHORT_ID,
                                                "테스트 기수",
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 12, 31),
                                                CohortStatus.ACTIVE)),
                                List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/me/access-context")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalRole").value("USER"))
                .andDo(document(
                        "cohort-core/get-my-access-context",
                        responseFields(accessContextFields())));
    }

    @Test
    @DisplayName("기수 상세 조회")
    void getsCohort() throws Exception {
        // Given: 기수 상세 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(cohortService.getCohort(COHORT_ID)).willReturn(cohortResponse(CohortStatus.ACTIVE));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohort-id}", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andDo(document(
                        "cohort-core/get",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        responseFields(cohortResponseFields())));
    }

    @Test
    @DisplayName("기수 수정")
    void updatesCohort() throws Exception {
        // Given: 기수 수정에 필요한 요청과 서비스 응답을 준비한다.
        UpdateCohortCommand command =
                new UpdateCohortCommand(
                        "수정된 기수", "수정 설명", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 11, 30));
        given(cohortService.update(COHORT_ID, command, USER_ID))
                .willReturn(cohortResponse(CohortStatus.PREPARING));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohorts/{cohort-id}", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"name":"수정된 기수","description":"수정 설명","startDate":"2026-02-01","endDate":"2026-11-30"}
                        """))
                .andExpect(status().isOk())
                .andDo(document(
                        "cohort-core/update",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        requestFields(cohortRequestFields()),
                        responseFields(cohortResponseFields())));
    }

    @Test
    @DisplayName("기수 상태 변경")
    void changesCohortStatus() throws Exception {
        // Given: 기수 상태 변경에 필요한 요청과 서비스 응답을 준비한다.
        given(
                        cohortService.changeStatus(
                                COHORT_ID,
                                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                                GlobalRole.SYSTEM_ADMIN))
                .willReturn(cohortResponse(CohortStatus.ACTIVE));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohorts/{cohort-id}/status", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andDo(document(
                        "cohort-core/change-status",
                        pathParameters(parameterWithName("cohort-id").description("기수 식별자")),
                        requestFields(fieldWithPath("status").description("변경할 기수 상태")),
                        responseFields(cohortResponseFields())));
    }

    @Test
    @DisplayName("기수 삭제")
    void deletesCohort() throws Exception {
        // Given: 기수 삭제에 필요한 요청과 서비스 응답을 준비한다.
        // When & Then
        mockMvc.perform(delete("/api/v1/cohorts/{cohort-id}", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "cohort-core/delete",
                        pathParameters(parameterWithName("cohort-id").description("삭제할 기수 식별자"))));

        verify(cohortService).delete(COHORT_ID, GlobalRole.SYSTEM_ADMIN);
    }

    @Test
    @DisplayName("잘못된 기수 생성 요청 시 400 응답")
    void rejectsInvalidCreateRequest() throws Exception {
        // Given: 잘못된 기수 생성 요청 400 응답에 필요한 요청과 서비스 응답을 준비한다.
        // When & Then
        mockMvc.perform(post("/api/v1/cohorts")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":null,\"startDate\":null,\"endDate\":null}"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "cohort-core/create-invalid",
                        requestFields(cohortRequestFields()),
                        responseFields(errorFields())));
    }

    private CohortResponse cohortResponse(CohortStatus status) {
        return new CohortResponse(
                COHORT_ID,
                "테스트 기수",
                "문서용 기수",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                status,
                USER_ID,
                OffsetDateTime.parse("2026-01-01T09:00:00+09:00"),
                OffsetDateTime.parse("2026-01-02T09:00:00+09:00"));
    }

    private static FieldDescriptor[] cohortRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("name").description("기수 이름"),
                    fieldWithPath("description").description("기수 설명 (nullable)"),
            fieldWithPath("startDate").description("기수 시작일 (`yyyy-MM-dd`)"),
                    fieldWithPath("endDate").description("기수 종료일 (`yyyy-MM-dd`)")
        };
    }

    private static FieldDescriptor[] cohortResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("기수 식별자"), fieldWithPath("name").description("기수 이름"),
                    fieldWithPath("description").description("기수 설명"),
            fieldWithPath("startDate").description("기수 시작일"),
                    fieldWithPath("endDate").description("기수 종료일"),
                    fieldWithPath("status").description("기수 상태"),
            fieldWithPath("createdByUserId").description("생성자 사용자 식별자"),
                    fieldWithPath("createdAt").description("생성 시각"),
                    fieldWithPath("updatedAt").description("수정 시각")
        };
    }

    private static FieldDescriptor[] cohortListFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].id").description("기수 식별자"),
                    fieldWithPath("[].name").description("기수 이름"),
                    fieldWithPath("[].description").description("기수 설명"),
            fieldWithPath("[].startDate").description("기수 시작일"),
                    fieldWithPath("[].endDate").description("기수 종료일"),
                    fieldWithPath("[].status").description("기수 상태"),
            fieldWithPath("[].createdByUserId").description("생성자 사용자 식별자"),
                    fieldWithPath("[].createdAt").description("생성 시각"),
                    fieldWithPath("[].updatedAt").description("수정 시각")
        };
    }

    private static FieldDescriptor[] adminSummaryFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].id").description("기수 식별자"),
                    fieldWithPath("[].name").description("기수 이름"),
                    fieldWithPath("[].description").description("기수 설명"),
            fieldWithPath("[].startDate").description("기수 시작일"),
                    fieldWithPath("[].endDate").description("기수 종료일"),
                    fieldWithPath("[].status").description("기수 상태"),
            fieldWithPath("[].memberCount").description("활성 구성원 수"),
                    fieldWithPath("[].managerUserIds").description("기수 관리자 사용자 식별자 목록"),
                    fieldWithPath("[].managerUserIds[]").description("관리자 사용자 식별자")
        };
    }

    private static FieldDescriptor[] accessContextFields() {
        return new FieldDescriptor[] {
            fieldWithPath("globalRole").description("사용자의 전역 역할"),
            fieldWithPath("accessType").description("우선 접근 유형"),
            fieldWithPath("managedCohorts").description("관리 중인 기수 목록"),
            fieldWithPath("managedCohorts[].cohortId").description("기수 식별자"),
            fieldWithPath("managedCohorts[].name").description("기수 이름"),
            fieldWithPath("managedCohorts[].startDate").description("기수 시작일"),
            fieldWithPath("managedCohorts[].endDate").description("기수 종료일"),
            fieldWithPath("managedCohorts[].status").description("기수 상태"),
            fieldWithPath("studentCohorts").description("학습자로 소속된 기수 목록")
        };
    }

    private static FieldDescriptor[] errorFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("오류 코드"),
            fieldWithPath("message").description("오류 메시지"),
            fieldWithPath("path").description("오류 요청 경로"),
            fieldWithPath("requestId").description("요청 추적 식별자 (없으면 null)")
        };
    }
}
