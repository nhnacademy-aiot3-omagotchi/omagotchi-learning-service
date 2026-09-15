package site.omagotchi.learningservice.space.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(SpaceAdminController.class)
@LearningRestDocsTest
class SpaceAdminControllerTest {

    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");

    private static final String AUTHORIZATION = "Bearer " + TestJwtKeyConfig.issue();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceCommandService spaceCommandService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Test
    @DisplayName("공간 생성")
    void createsSpace() throws Exception {
        // Given: 공간 생성 요청과 응답 준비
        when(spaceCommandService.create(any(CreateSpaceCommand.class), any(UUID.class)))
                .thenReturn(space(SpaceOperationalStatus.ACTIVE, null));

        // When & Then
        mockMvc.perform(post("/api/v1/admin/spaces")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andDo(document(
                        "spaces/create",
                        requestFields(
                                fieldWithPath("name")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 유형"),
                                fieldWithPath("capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원"),
                                fieldWithPath("cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("배정할 기수 ID")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("name")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 유형"),
                                fieldWithPath("capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원"),
                                fieldWithPath("cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("배정된 기수 ID"),
                                fieldWithPath("createdAt")
                                        .type(JsonFieldType.STRING)
                                        .description("생성 시각"))));
    }

    @Test
    @DisplayName("공간 수정")
    void updatesSpace() throws Exception {
        // Given: 공간 수정 요청과 응답 준비
        when(spaceCommandService.update(
                        any(Long.class), any(UpdateSpaceCommand.class), any(UUID.class)))
                .thenReturn(space(SpaceOperationalStatus.ACTIVE, null));

        // When & Then
        mockMvc.perform(put("/api/v1/admin/spaces/{space-id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"회의실 A\",\"type\":\"MEETING\",\"capacity\":8}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/update",
                        pathParameters(parameterWithName("space-id").description("수정할 공간 ID")),
                        requestFields(
                                fieldWithPath("name")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 유형"),
                                fieldWithPath("capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("name")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 이름"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("공간 유형"),
                                fieldWithPath("capacity")
                                        .type(JsonFieldType.NUMBER)
                                        .description("수용 인원"),
                                fieldWithPath("updatedAt")
                                        .type(JsonFieldType.STRING)
                                        .description("수정 시각"))));
    }

    @Test
    @DisplayName("공간 삭제")
    void deletesSpace() throws Exception {
        // Given: 삭제 대상과 관리자 인증 준비
        // When & Then
        mockMvc.perform(delete("/api/v1/admin/spaces/{space-id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "spaces/delete",
                        pathParameters(parameterWithName("space-id").description("삭제할 공간 ID"))));
    }

    @Test
    void mapsDuplicateSpaceNameToConflictResponse() throws Exception {
        when(spaceCommandService.create(any(CreateSpaceCommand.class), any(UUID.class)))
                .thenThrow(new BusinessException(SpaceErrorCode.DUPLICATE_NAME));

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 공간 이름입니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/spaces"));
    }

    @Test
    void mapsSpaceNotFoundToNotFoundResponse() throws Exception {
        doThrow(new BusinessException(SpaceErrorCode.NOT_FOUND))
                .when(spaceCommandService)
                .delete(999L, USER_ID);

        mockMvc.perform(delete("/api/v1/admin/spaces/999")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("공간을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/spaces/999"));
    }

    @Test
    void mapsInvalidSpaceNameToBadRequestResponse() throws Exception {
        when(spaceCommandService.create(any(CreateSpaceCommand.class), any(UUID.class)))
                .thenThrow(new BusinessException(SpaceErrorCode.INVALID_NAME));

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPACE_INVALID_NAME"))
                .andExpect(jsonPath("$.message").value("공간 이름이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/spaces"));
    }

    @Test
    void mapsInvalidSpaceCapacityToBadRequestResponse() throws Exception {
        when(spaceCommandService.create(any(CreateSpaceCommand.class), any(UUID.class)))
                .thenThrow(new BusinessException(SpaceErrorCode.INVALID_CAPACITY));

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPACE_INVALID_CAPACITY"))
                .andExpect(jsonPath("$.message").value("공간 최대 인원이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/spaces"));
    }

    @Test
    void keepsBeanValidationResponseSeparateFromDomainErrors() throws Exception {
        mockMvc.perform(post("/api/v1/admin/spaces")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "name": "   ",
                          "type": "MEETING",
                          "capacity": 8
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/spaces"));

        verify(spaceCommandService, never())
                .create(any(CreateSpaceCommand.class), any(UUID.class));
    }

    @Test
    void activatesSpaceAndReturnsChangedStatus() throws Exception {
        when(spaceCommandService.activate(1L, USER_ID))
                .thenReturn(space(SpaceOperationalStatus.ACTIVE, null));

        mockMvc.perform(post("/api/v1/admin/spaces/{space-id}/activate", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/activate",
                        pathParameters(parameterWithName("space-id").description("활성화할 공간 ID")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("operationalStatus")
                                        .type(JsonFieldType.STRING)
                                        .description("운영 상태"),
                                fieldWithPath("inactiveReason")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("비활성화 사유"),
                                fieldWithPath("updatedAt")
                                        .type(JsonFieldType.STRING)
                                        .description("수정 시각 (ISO-8601)"))))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").isEmpty());
    }

    @Test
    void deactivatesSpaceWithReason() throws Exception {
        when(spaceCommandService.deactivate(1L, "정기 점검", USER_ID))
                .thenReturn(space(SpaceOperationalStatus.INACTIVE, "정기 점검"));

        mockMvc.perform(post("/api/v1/admin/spaces/{space-id}/deactivate", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"inactiveReason":"정기 점검"}
                        """))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/deactivate",
                        pathParameters(parameterWithName("space-id").description("비활성화할 공간 ID")),
                        requestFields(
                                fieldWithPath("inactiveReason")
                                        .type(JsonFieldType.STRING)
                                        .description("비활성화 사유")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("operationalStatus")
                                        .type(JsonFieldType.STRING)
                                        .description("운영 상태"),
                                fieldWithPath("inactiveReason")
                                        .type(JsonFieldType.STRING)
                                        .description("비활성화 사유"),
                                fieldWithPath("updatedAt")
                                        .type(JsonFieldType.STRING)
                                        .description("수정 시각 (ISO-8601)"))))
                .andExpect(jsonPath("$.operationalStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").value("정기 점검"));
    }

    @Test
    void rejectsNullEmptyAndBlankDeactivationReasonAtRequestBoundary()
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/spaces/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"inactiveReason":null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/admin/spaces/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"inactiveReason":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/admin/spaces/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"inactiveReason":"   "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never())
                .deactivate(any(Long.class), any(String.class), any(UUID.class));
    }

    @Test
    void mapsActiveOccupancyConflict() throws Exception {
        when(spaceCommandService.deactivate(1L, "점검", USER_ID))
                .thenThrow(new BusinessException(SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS));

        mockMvc.perform(post("/api/v1/admin/spaces/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"inactiveReason":"점검"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_ACTIVE_OCCUPANCY_EXISTS"));
    }

    @Test
    void assignsAndUnassignsLabCohort() throws Exception {
        when(spaceCommandService.assignCohort(1L, 42L, USER_ID)).thenReturn(lab(42L));
        when(spaceCommandService.unassignCohort(1L, USER_ID)).thenReturn(lab(null));

        mockMvc.perform(put("/api/v1/admin/spaces/{space-id}/cohort", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"cohortId":42}
                        """))
                .andExpect(status().isOk())
                .andDo(document(
                        "spaces/assign-cohort",
                        pathParameters(parameterWithName("space-id").description("배정할 공간 ID")),
                        requestFields(fieldWithPath("cohortId").type(JsonFieldType.NUMBER).description("배정할 기수 ID")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("공간 ID"),
                                fieldWithPath("cohortId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("배정된 기수 ID"),
                                fieldWithPath("updatedAt")
                                        .type(JsonFieldType.STRING)
                                        .description("수정 시각 (ISO-8601)"))))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cohortId").value(42))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(delete("/api/v1/admin/spaces/{space-id}/cohort", 1L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNoContent())
                .andDo(document(
                        "spaces/unassign-cohort",
                        pathParameters(parameterWithName("space-id").description("배정을 해제할 공간 ID"))));
    }

    @Test
    void rejectsMissingAssignmentCohortIdAtRequestBoundary()
            throws Exception {
        mockMvc.perform(put("/api/v1/admin/spaces/1/cohort")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never()).assignCohort(
                any(Long.class),
                any(Long.class),
                any(UUID.class));
    }

    @Test
    void rejectsMissingAndNullUpdateType() throws Exception {
        String missingType = """
                {"name":"회의실 A","capacity":8}
                """;
        String nullType = """
                {"name":"회의실 A","type":null,"capacity":8}
                """;

        mockMvc.perform(put("/api/v1/admin/spaces/1")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(put("/api/v1/admin/spaces/1")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never())
                .update(any(Long.class), any(UpdateSpaceCommand.class), any(UUID.class));
    }

    @Test
    void rejectsUnknownUpdateTypeString() throws Exception {
        mockMvc.perform(put("/api/v1/admin/spaces/1")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "name":"회의실 A",
                          "type":"UNKNOWN",
                          "capacity":8
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_MALFORMED_REQUEST"));

        verify(spaceCommandService, never())
                .update(any(Long.class), any(UpdateSpaceCommand.class), any(UUID.class));
    }

    private String validRequest() {
        return """
                {
                  "name": "회의실 A",
                  "type": "MEETING",
                  "capacity": 8,
                  "cohortId": 42
                }
                """;
    }

    private Space space(
            SpaceOperationalStatus status,
            String reason
    ) {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 29, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        return Space.restore(
                1L,
                42L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                status,
                reason,
                now.minusDays(1),
                now,
                null
        );
    }

    private Space lab(Long cohortId) {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 29, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        return Space.restore(
                1L,
                cohortId,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                now.minusDays(1),
                now,
                null
        );
    }
}
