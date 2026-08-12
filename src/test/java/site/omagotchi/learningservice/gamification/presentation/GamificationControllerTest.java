package site.omagotchi.learningservice.gamification.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.gamification.application.CharacterOnboardingService;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.GamificationProgressionService;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.application.result.GameCharacterResult;
import site.omagotchi.learningservice.gamification.application.result.UserCharacterResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GamificationController.class)
@Import({
        SecurityConfig.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        SecurityErrorResponseHandler.class,
        TestJwtKeyConfig.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@DisplayName("게이미피케이션 API")
class GamificationControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CharacterOnboardingService characterOnboardingService;

    @MockitoBean
    private DailyQuestService dailyQuestService;

    @MockitoBean
    private GamificationProgressionService gamificationProgressionService;

    @Test
    @DisplayName("선택 가능한 캐릭터 목록을 조회한다")
    void getsCharacters() throws Exception {
        given(characterOnboardingService.getAvailableCharacters())
                .willReturn(List.of(new GameCharacterResult(1L, "NIGHT_CLASS", "야간반", "기본 캐릭터")));

        mockMvc.perform(get("/api/v1/gamification/characters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameCharacterId").value(1))
                .andExpect(jsonPath("$[0].code").value("NIGHT_CLASS"))
                .andExpect(jsonPath("$[0].name").value("야간반"))
                .andExpect(jsonPath("$[0].description").value("기본 캐릭터"));
    }

    @Test
    @DisplayName("대표 캐릭터 생성 요청은 JWT 사용자로 서비스에 위임한다")
    void createsRepresentativeCharacter() throws Exception {
        given(characterOnboardingService.createRepresentativeCharacter(
                eq(USER_ID),
                eq(new CreateUserCharacterCommand(1L, "오마"))
        )).willReturn(new UserCharacterResult(
                10L,
                1L,
                "NIGHT_CLASS",
                "야간반",
                "오마",
                "오마",
                0,
                1,
                AdvancementStage.BASE,
                true
        ));

        mockMvc.perform(post("/api/v1/gamification/characters/representative")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gameCharacterId": 1,
                                  "nickname": "오마"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCharacterId").value(10))
                .andExpect(jsonPath("$.gameCharacterId").value(1))
                .andExpect(jsonPath("$.gameCharacterCode").value("NIGHT_CLASS"))
                .andExpect(jsonPath("$.gameCharacterName").value("야간반"))
                .andExpect(jsonPath("$.nickname").value("오마"))
                .andExpect(jsonPath("$.representative").value(true));

        verify(characterOnboardingService).createRepresentativeCharacter(
                USER_ID,
                new CreateUserCharacterCommand(1L, "오마")
        );
    }
}
