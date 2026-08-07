package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaceAdminControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1"
    );

    private SpaceCommandService spaceCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        spaceCommandService = mock(SpaceCommandService.class);
        SpaceAdminController controller = new SpaceAdminController(
                spaceCommandService
        );

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultRequest(get("/")
                        .header("X-User-Id", USER_ID))
                .build();
    }

    @Test
    void mapsDuplicateSpaceNameToConflictResponse() throws Exception {
        when(spaceCommandService.create(
                any(CreateSpaceCommand.class),
                any(UUID.class),
                any(GlobalRole.class)
        ))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.DUPLICATE_NAME
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("SPACE_DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 공간 이름입니다."))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));
    }

    @Test
    void mapsSpaceNotFoundToNotFoundResponse() throws Exception {
        doThrow(new BusinessException(SpaceErrorCode.NOT_FOUND))
                .when(spaceCommandService)
                .delete(999L, USER_ID, GlobalRole.USER);

        mockMvc.perform(delete("/api/admin/spaces/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("공간을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path")
                        .value("/api/admin/spaces/999"));
    }

    @Test
    void mapsInvalidSpaceNameToBadRequestResponse() throws Exception {
        when(spaceCommandService.create(
                any(CreateSpaceCommand.class),
                any(UUID.class),
                any(GlobalRole.class)
        ))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.INVALID_NAME
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("SPACE_INVALID_NAME"))
                .andExpect(jsonPath("$.message")
                        .value("공간 이름이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));
    }

    @Test
    void mapsInvalidSpaceCapacityToBadRequestResponse() throws Exception {
        when(spaceCommandService.create(
                any(CreateSpaceCommand.class),
                any(UUID.class),
                any(GlobalRole.class)
        ))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.INVALID_CAPACITY
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code")
                        .value("SPACE_INVALID_CAPACITY"))
                .andExpect(jsonPath("$.message")
                        .value("공간 최대 인원이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));
    }

    @Test
    void keepsBeanValidationResponseSeparateFromDomainErrors() throws Exception {
        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "type": "MEETING",
                                  "capacity": 8
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));

        verify(spaceCommandService, never())
                .create(
                        any(CreateSpaceCommand.class),
                        any(UUID.class),
                        any(GlobalRole.class)
                );
    }

    @Test
    void activatesSpaceAndReturnsChangedStatus() throws Exception {
        when(spaceCommandService.activate(
                1L,
                USER_ID,
                GlobalRole.USER
        ))
                .thenReturn(space(SpaceOperationalStatus.ACTIVE, null));

        mockMvc.perform(patch("/api/admin/spaces/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").isEmpty());
    }

    @Test
    void deactivatesSpaceWithReason() throws Exception {
        when(spaceCommandService.deactivate(
                1L,
                "정기 점검",
                USER_ID,
                GlobalRole.USER
        ))
                .thenReturn(space(
                        SpaceOperationalStatus.INACTIVE,
                        "정기 점검"
                ));

        mockMvc.perform(patch("/api/admin/spaces/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"정기 점검"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").value("정기 점검"));
    }

    @Test
    void rejectsNullEmptyAndBlankDeactivationReasonAtRequestBoundary()
            throws Exception {
        mockMvc.perform(patch("/api/admin/spaces/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(patch("/api/admin/spaces/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(patch("/api/admin/spaces/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never())
                .deactivate(
                        any(Long.class),
                        any(String.class),
                        any(UUID.class),
                        any(GlobalRole.class)
                );
    }

    @Test
    void mapsActiveOccupancyConflict() throws Exception {
        when(spaceCommandService.deactivate(
                1L,
                "점검",
                USER_ID,
                GlobalRole.USER
        ))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS
                ));

        mockMvc.perform(patch("/api/admin/spaces/1/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACTIVE_OCCUPANCY_EXISTS"));
    }

    @Test
    void assignsAndUnassignsLabCohort() throws Exception {
        when(spaceCommandService.assignCohort(
                1L,
                42L,
                USER_ID,
                GlobalRole.USER
        )).thenReturn(lab(42L));
        when(spaceCommandService.unassignCohort(
                1L,
                USER_ID,
                GlobalRole.SYSTEM_ADMIN
        )).thenReturn(lab(null));

        mockMvc.perform(put("/api/admin/spaces/1/cohort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":42}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cohortId").value(42))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(delete("/api/admin/spaces/1/cohort")
                        .header("X-Global-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cohortId").isEmpty());
    }

    @Test
    void rejectsMissingAssignmentCohortIdAtRequestBoundary()
            throws Exception {
        mockMvc.perform(put("/api/admin/spaces/1/cohort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never()).assignCohort(
                any(Long.class),
                any(Long.class),
                any(UUID.class),
                any(GlobalRole.class)
        );
    }

    @Test
    void rejectsMissingAndNullUpdateType() throws Exception {
        String missingType = """
                {"name":"회의실 A","capacity":8}
                """;
        String nullType = """
                {"name":"회의실 A","type":null,"capacity":8}
                """;

        mockMvc.perform(put("/api/admin/spaces/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
        mockMvc.perform(put("/api/admin/spaces/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));

        verify(spaceCommandService, never())
                .update(
                        any(Long.class),
                        any(UpdateSpaceCommand.class),
                        any(UUID.class),
                        any(GlobalRole.class)
                );
    }

    @Test
    void rejectsUnknownUpdateTypeString() throws Exception {
        mockMvc.perform(put("/api/admin/spaces/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"회의실 A",
                                  "type":"UNKNOWN",
                                  "capacity":8
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_MALFORMED_REQUEST"));

        verify(spaceCommandService, never())
                .update(
                        any(Long.class),
                        any(UpdateSpaceCommand.class),
                        any(UUID.class),
                        any(GlobalRole.class)
                );
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
        ZonedDateTime now = ZonedDateTime.of(
                2026, 7, 29, 10, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );

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
        ZonedDateTime now = ZonedDateTime.of(
                2026, 7, 29, 10, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );

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
