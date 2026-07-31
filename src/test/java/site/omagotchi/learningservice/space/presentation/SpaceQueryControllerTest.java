package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.space.application.query.SpaceListItem;
import site.omagotchi.learningservice.space.application.query.SpaceQueryService;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpaceQueryController.class)
@WithMockUser
class SpaceQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceQueryService spaceQueryService;

    @Test
    void returnsEmptyJsonArrayWhenNoSpacesExist() throws Exception {
        when(spaceQueryService.getSpaceList()).thenReturn(List.of());

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(spaceQueryService).getSpaceList();
    }

    @Test
    void returnsActualResponseFieldsAndSerializedEnums() throws Exception {
        ZonedDateTime expiresAt = ZonedDateTime.of(
                2026, 7, 27, 15, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );
        when(spaceQueryService.getSpaceList()).thenReturn(List.of(
                new SpaceListItem(
                        1L,
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        11L,
                        SpaceUsageStatus.OCCUPIED,
                        expiresAt,
                        1800L
                )
        ));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(1))
                .andExpect(jsonPath("$[0].name").value("회의실 A"))
                .andExpect(jsonPath("$[0].type").value("MEETING"))
                .andExpect(jsonPath("$[0].capacity").value(8))
                .andExpect(jsonPath("$[0].operationalStatus")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$[0].inactiveReason").isEmpty())
                .andExpect(jsonPath("$[0].cohortId").value(11))
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$[0].occupancyExpiresAt")
                        .value("2026-07-27T15:00:00+09:00"))
                .andExpect(jsonPath("$[0].remainingTimeSeconds")
                        .value(1800));

        verify(spaceQueryService).getSpaceList();
    }
}
