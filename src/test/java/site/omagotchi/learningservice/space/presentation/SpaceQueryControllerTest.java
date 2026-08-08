package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(SpaceQueryController.class)
@WithMockUser
class SpaceQueryControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpaceQueryService spaceQueryService;

    @Test
    void returnsEmptyJsonArrayWhenNoSpacesExist() throws Exception {
        when(spaceQueryService.getSpaceList(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(spaceQueryService).getSpaceList(null);
    }

    @Test
    void returnsActualResponseFieldsAndSerializedEnums() throws Exception {
        ZonedDateTime expiresAt = ZonedDateTime.of(
                2026, 7, 27, 15, 0, 0, 0,
                ZoneId.of("Asia/Seoul")
        );
        when(spaceQueryService.getSpaceList(null)).thenReturn(List.of(
                new SpaceListResult(
                        1L,
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        11L,
                        SpaceUsageStatus.OCCUPIED,
                        expiresAt,
                        1800L,
                        false,
                        null,
                        null,
                        null,
                        null
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
                        .value(1800))
                .andExpect(jsonPath("$[0].occupiedBySameCohort")
                        .value(false));

        verify(spaceQueryService).getSpaceList(null);
    }

    @Test
    void usesAuthenticatedPrincipalInsteadOfUserHeader() throws Exception {
        UUID spoofedUserId = UUID.randomUUID();
        when(spaceQueryService.getSpaceList(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/spaces")
                        .with(jwt().jwt(token -> token
                                .subject(USER_ID.toString())
                                .claim("role", "USER")))
                        .header("X-User-Id", spoofedUserId))
                .andExpect(status().isOk());

        verify(spaceQueryService).getSpaceList(USER_ID);
    }
}
