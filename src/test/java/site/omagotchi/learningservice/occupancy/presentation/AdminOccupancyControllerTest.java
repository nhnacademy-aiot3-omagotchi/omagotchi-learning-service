package site.omagotchi.learningservice.occupancy.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.occupancy.application.AdminOccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.result.AdminActiveOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = AdminOccupancyController.class)
@LearningRestDocsTest
class AdminOccupancyControllerTest {

    private static final UUID MANAGER_ID = UUID.randomUUID();

    @MockitoBean
    private AdminOccupancyQueryService service;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    private static final String AUTHORIZATION =
            "Bearer "
                    + TestJwtKeyConfig.issue(
                            TestJwtKeyConfig.ISSUER,
                            TestJwtKeyConfig.AUDIENCE,
                            MANAGER_ID.toString(),
                            "SYSTEM_ADMIN");

    @Test
    void returnsActiveOccupancies() throws Exception {
        UUID occupierId = UUID.randomUUID();
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-28T09:00:00+09:00");
        OffsetDateTime expiresAt = startedAt.plusHours(2);
        given(service.getActiveOccupancies(MANAGER_ID)).willReturn(List.of(
                new AdminActiveOccupancyResult(
                        1L, "회의실 A", 10L, occupierId, "점유자", 2,
                        startedAt, expiresAt, 3600L, OccupancyStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/admin/spaces/occupancies")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(1L))
                .andExpect(jsonPath("$[0].occupierDisplayName").value("점유자"))
                .andExpect(jsonPath("$[0].participantCount").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
}
