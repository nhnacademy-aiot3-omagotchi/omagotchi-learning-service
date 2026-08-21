package site.omagotchi.learningservice.realtime.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.realtime.application.CohortPresenceService;
import site.omagotchi.learningservice.realtime.application.CohortPresenceSnapshot;
import site.omagotchi.learningservice.realtime.application.PresenceStatus;
import site.omagotchi.learningservice.realtime.application.PresenceCharacterSnapshot;
import site.omagotchi.learningservice.realtime.application.PresenceUserSnapshot;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresenceController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
@DisplayName("기수 Presence API 계약")
class PresenceControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CohortPresenceService presenceService;

    @Test
    @DisplayName("현재 사용자의 기수 Presence snapshot을 반환한다")
    void getsMyCohortPresence() throws Exception {
        given(presenceService.currentUserSnapshot(USER_ID)).willReturn(new CohortPresenceSnapshot(
                1L,
                List.of(new PresenceUserSnapshot(
                        USER_ID,
                        "오마",
                        new PresenceCharacterSnapshot("night", "pistachio", "night/pistachio"),
                        PresenceStatus.ONLINE
                )),
                OffsetDateTime.parse("2026-08-20T15:00:00+09:00")
        ));

        mockMvc.perform(get("/api/v1/cohorts/me/presence")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.users[0].status").value("ONLINE"))
                .andExpect(jsonPath("$.users[0].nickname").value("오마"))
                .andExpect(jsonPath("$.users[0].currentCharacter.type").value("night"))
                .andExpect(jsonPath("$.users[0].currentCharacter.colorId").value("pistachio"))
                .andExpect(jsonPath("$.users[0].currentCharacter.assetKey").value("night/pistachio"))
                .andExpect(jsonPath("$.occurredAt").value("2026-08-20T15:00:00+09:00"))
                .andDo(document("presence/get-my-cohort-snapshot"));

        verify(presenceService).currentUserSnapshot(USER_ID);
    }
}
