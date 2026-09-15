package site.omagotchi.learningservice.occupancy.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.occupancy.application.AdminOccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.result.AdminActiveOccupancyResult;
import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest({VacancyAlertController.class, AdminOccupancyController.class})
@LearningRestDocsTest
class VacancyAlertDocumentationTest {
    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VacancyAlertService vacancyAlertService;

    @MockitoBean
    private AdminOccupancyQueryService adminOccupancyQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Test
    @DisplayName("공실 알림 신청")
    void requestsVacancyAlert() throws Exception {
        // Given: 공실 알림 신청에 필요한 요청과 서비스 응답을 준비한다.
        // When & Then
        mockMvc.perform(post("/api/v1/spaces/{space-id}/vacancy-alerts", 1L)
                        .contentType("application/json")
                        .content("{\"cohortId\":10}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isCreated())
                .andDo(document(
                        "spaces/request-vacancy-alert",
                        pathParameters(parameterWithName("space-id").description("공실 알림을 신청할 공간 ID")),
                        requestFields(
                                fieldWithPath("cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("신청 주체로 사용할 기수 ID"))));
    }

    @Test
    @DisplayName("내 공실 알림 조회")
    void listsMyVacancyAlerts() throws Exception {
        // Given: 내 공실 알림 조회에 필요한 요청과 서비스 응답을 준비한다.
        given(vacancyAlertService.findMine(USER_ID))
                .willReturn(
                        List.of(
                                new VacancyAlertView(
                                        20L,
                                        1L,
                                        10L,
                                        OffsetDateTime.parse("2026-08-28T09:00:00+09:00"))));

        // When & Then
        mockMvc.perform(get("/api/v1/vacancy-alerts/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/list-my-vacancy-alerts",
                        responseFields(
                                fieldWithPath("[].alertId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("알림 신청 ID"),
                                fieldWithPath("[].spaceId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("[].cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("신청 기수 ID"),
                                fieldWithPath("[].createdAt")
                                        .type(JsonFieldType.STRING)
                                        .description("신청 시각"))));
    }

    @Test
    @DisplayName("공실 알림 취소")
    void cancelsVacancyAlert() throws Exception {
        // Given: 공실 알림 취소에 필요한 요청과 서비스 응답을 준비한다.
        // When & Then
        mockMvc.perform(delete("/api/v1/vacancy-alerts/{alert-id}", 20L)
                        .header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "spaces/cancel-vacancy-alert",
                        pathParameters(parameterWithName("alert-id").description("취소할 알림 신청 ID"))));
    }

    @Test
    @DisplayName("관리자 활성 점유 조회")
    void listsActiveOccupanciesForAdmin() throws Exception {
        // Given: 관리자 활성 점유 조회에 필요한 요청과 서비스 응답을 준비한다.
        OffsetDateTime start = OffsetDateTime.parse("2026-08-28T09:00:00+09:00");
        given(adminOccupancyQueryService.getActiveOccupancies(USER_ID))
                .willReturn(
                        List.of(
                                new AdminActiveOccupancyResult(
                                        1L,
                                        "회의실 A",
                                        10L,
                                        USER_ID,
                                        "오마",
                                        2,
                                        start,
                                        start.plusHours(2),
                                        3600L,
                                        OccupancyStatus.ACTIVE)));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/spaces/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/list-active-occupancies",
                        responseFields(
                                fieldWithPath("[].spaceId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("[].spaceName")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("[].occupancyId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("점유 ID"),
                                fieldWithPath("[].occupierUserId")
                                        .type(JsonFieldType.STRING)
                                        .description("점유자 사용자 ID"),
                                fieldWithPath("[].occupierDisplayName")
                                        .type(JsonFieldType.STRING)
                                        .description("점유자 표시 이름"),
                                fieldWithPath("[].participantCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("참여자 수"),
                                fieldWithPath("[].startedAt")
                                        .type(JsonFieldType.STRING)
                                        .description("점유 시작 시각"),
                                fieldWithPath("[].expiresAt")
                                        .type(JsonFieldType.STRING)
                                        .description("점유 만료 시각"),
                                fieldWithPath("[].remainingTimeSeconds")
                                        .type(JsonFieldType.NUMBER)
                                        .description("남은 시간"),
                                fieldWithPath("[].status")
                                        .type(JsonFieldType.STRING)
                                        .description("점유 상태"))));
    }

    private static String bearer(String role) {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        TestJwtKeyConfig.ISSUER,
                        TestJwtKeyConfig.AUDIENCE,
                        USER_ID.toString(),
                        role);
    }
}
