package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaceAdminControllerTest {

    private CreateSpaceUseCase createSpaceUseCase;
    private DeleteSpaceUseCase deleteSpaceUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createSpaceUseCase = mock(CreateSpaceUseCase.class);
        UpdateSpaceUseCase updateSpaceUseCase =
                mock(UpdateSpaceUseCase.class);
        deleteSpaceUseCase = mock(DeleteSpaceUseCase.class);
        SpaceAdminController controller = new SpaceAdminController(
                createSpaceUseCase,
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

    private String validRequest() {
        return """
                {
                  "name": "회의실 A",
                  "type": "MEETING",
                  "capacity": 8
                }
                """;
    }
}
