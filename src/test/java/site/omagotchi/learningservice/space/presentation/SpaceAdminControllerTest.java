package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.ActivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeactivateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaceAdminControllerTest {

    private CreateSpaceUseCase createSpaceUseCase;
    private DeleteSpaceUseCase deleteSpaceUseCase;
    private ActivateSpaceUseCase activateSpaceUseCase;
    private DeactivateSpaceUseCase deactivateSpaceUseCase;
    private UpdateSpaceUseCase updateSpaceUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createSpaceUseCase = mock(CreateSpaceUseCase.class);
        activateSpaceUseCase = mock(ActivateSpaceUseCase.class);
        deactivateSpaceUseCase = mock(DeactivateSpaceUseCase.class);
        updateSpaceUseCase = mock(UpdateSpaceUseCase.class);
        deleteSpaceUseCase = mock(DeleteSpaceUseCase.class);
        SpaceAdminController controller = new SpaceAdminController(
                createSpaceUseCase,
                activateSpaceUseCase,
                deactivateSpaceUseCase,
                updateSpaceUseCase,
                deleteSpaceUseCase
        );

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsDuplicateSpaceNameToConflictResponse() throws Exception {
        when(createSpaceUseCase.create(any(CreateSpaceCommand.class)))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.DUPLICATE_NAME
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message")
                        .value("이미 사용 중인 공간 이름입니다."))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));
    }

    @Test
    void mapsSpaceNotFoundToNotFoundResponse() throws Exception {
        doThrow(new BusinessException(SpaceErrorCode.NOT_FOUND))
                .when(deleteSpaceUseCase)
                .delete(999L);

        mockMvc.perform(delete("/api/admin/spaces/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("공간을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path")
                        .value("/api/admin/spaces/999"));
    }

    @Test
    void mapsInvalidSpaceNameToBadRequestResponse() throws Exception {
        when(createSpaceUseCase.create(any(CreateSpaceCommand.class)))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.INVALID_NAME
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPACE_INVALID_NAME"))
                .andExpect(jsonPath("$.message")
                        .value("공간 이름이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));
    }

    @Test
    void mapsInvalidSpaceCapacityToBadRequestResponse() throws Exception {
        when(createSpaceUseCase.create(any(CreateSpaceCommand.class)))
                .thenThrow(new BusinessException(
                        SpaceErrorCode.INVALID_CAPACITY
                ));

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
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
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/admin/spaces"));

        verify(createSpaceUseCase, never())
                .create(any(CreateSpaceCommand.class));
    }

    @Test
    void activatesSpaceAndReturnsChangedStatus() throws Exception {
        when(activateSpaceUseCase.activate(1L))
                .thenReturn(space(SpaceOperationalStatus.ACTIVE, null));

        mockMvc.perform(patch("/api/admin/spaces/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").isEmpty());
    }

    @Test
    void deactivatesSpaceWithReason() throws Exception {
        when(deactivateSpaceUseCase.deactivate(1L, "정기 점검"))
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

        verify(deactivateSpaceUseCase, never())
                .deactivate(any(Long.class), any(String.class));
    }

    @Test
    void mapsActiveOccupancyConflict() throws Exception {
        when(deactivateSpaceUseCase.deactivate(1L, "점검"))
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

        verify(updateSpaceUseCase, never())
                .update(any(Long.class), any(UpdateSpaceCommand.class));
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

        verify(updateSpaceUseCase, never())
                .update(any(Long.class), any(UpdateSpaceCommand.class));
    }

    private String validRequest() {
        return """
                {
                  "name": "회의실 A",
                  "type": "MEETING",
                  "capacity": 8
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
                null,
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
}
