package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.occupancy.application.AdminOccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.result.AdminActiveOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminOccupancyControllerTest {

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private AdminOccupancyQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminOccupancyQueryService.class);
        mockMvc = standaloneSetup(new AdminOccupancyController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(MANAGER_ID.toString())
                .claim("role", "USER")
                .build();
        TestSecurityContextHolder.setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void returnsActiveOccupancies() throws Exception {
        UUID occupierId = UUID.randomUUID();
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-28T09:00:00+09:00");
        OffsetDateTime expiresAt = startedAt.plusHours(2);
        given(service.getActiveOccupancies(MANAGER_ID)).willReturn(List.of(
                new AdminActiveOccupancyResult(
                        1L, "회의실 A", 10L, occupierId, "점유자", 2,
                        startedAt, expiresAt, 3600L, OccupancyStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/admin/spaces/occupancies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spaceId").value(1L))
                .andExpect(jsonPath("$[0].occupierDisplayName").value("점유자"))
                .andExpect(jsonPath("$[0].participantCount").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }
}
