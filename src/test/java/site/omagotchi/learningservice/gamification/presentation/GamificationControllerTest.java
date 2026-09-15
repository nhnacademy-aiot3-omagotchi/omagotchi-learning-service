package site.omagotchi.learningservice.gamification.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.LocalDate;
import java.util.List;
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
import site.omagotchi.learningservice.gamification.application.CharacterOnboardingService;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.GamificationProgressionService;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.application.result.CharacterGrowthResult;
import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.application.result.GameCharacterResult;
import site.omagotchi.learningservice.gamification.application.result.GamificationProgressionResult;
import site.omagotchi.learningservice.gamification.application.result.HomeResult;
import site.omagotchi.learningservice.gamification.application.result.UserCharacterResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.QuestType;
import site.omagotchi.learningservice.gamification.domain.StudyProgressionState;
import site.omagotchi.learningservice.gamification.domain.WeekdayStreakState;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = GamificationController.class)
@DisplayName("게이미피케이션 API")
@LearningRestDocsTest
class GamificationControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

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
                .willReturn(List.of(new GameCharacterResult(1L, "NIGHT_CLASS", "night", "야간반", "기본 캐릭터")));

        mockMvc.perform(get("/api/v1/gamification/characters")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameCharacterId").value(1))
                .andExpect(jsonPath("$[0].code").value("NIGHT_CLASS"))
                .andExpect(jsonPath("$[0].assetKey").value("night"))
                .andExpect(jsonPath("$[0].name").value("야간반"))
                .andExpect(jsonPath("$[0].description").value("기본 캐릭터"))
                .andDo(document(
                        "gamification/get-characters",
                        responseFields(
                                fieldWithPath("[].gameCharacterId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("게임 캐릭터 ID"),
                                fieldWithPath("[].code")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 코드"),
                                fieldWithPath("[].assetKey")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 에셋 키"),
                                fieldWithPath("[].name")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 이름"),
                                fieldWithPath("[].description")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 설명"))));
    }

    @Test
    @DisplayName("대표 캐릭터 생성 요청은 JWT 사용자로 서비스에 위임한다")
    void createsRepresentativeCharacter() throws Exception {
        given(characterOnboardingService.createRepresentativeCharacter(
                eq(USER_ID),
                eq(new CreateUserCharacterCommand(1L, "오마", "pistachio"))
        )).willReturn(new UserCharacterResult(
                10L,
                1L,
                "NIGHT_CLASS",
                "night",
                "pistachio",
                "night/pistachio",
                "야간반",
                "오마",
                "오마",
                0,
                1,
                AdvancementStage.BASE,
                true
        ));

        mockMvc.perform(post("/api/v1/gamification/characters/representative")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "gameCharacterId": 1,
                          "nickname": "오마",
                          "colorId": "pistachio"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCharacterId").value(10))
                .andExpect(jsonPath("$.gameCharacterId").value(1))
                .andExpect(jsonPath("$.gameCharacterCode").value("NIGHT_CLASS"))
                .andExpect(jsonPath("$.type").value("night"))
                .andExpect(jsonPath("$.colorId").value("pistachio"))
                .andExpect(jsonPath("$.assetKey").value("night/pistachio"))
                .andExpect(jsonPath("$.gameCharacterName").value("야간반"))
                .andExpect(jsonPath("$.nickname").value("오마"))
                .andExpect(jsonPath("$.representative").value(true))
                .andDo(document(
                        "gamification/create-representative-character",
                        requestFields(
                                fieldWithPath("gameCharacterId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("생성할 게임 캐릭터 ID"),
                                fieldWithPath("nickname")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 닉네임"),
                                fieldWithPath("colorId")
                                        .type(JsonFieldType.STRING)
                                        .optional()
                                        .description("캐릭터 색상 ID")),
                        responseFields(
                                fieldWithPath("userCharacterId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("사용자 캐릭터 ID"),
                                fieldWithPath("gameCharacterId")
                                        .type(JsonFieldType.NUMBER)
                                        .description("게임 캐릭터 ID"),
                                fieldWithPath("gameCharacterCode")
                                        .type(JsonFieldType.STRING)
                                        .description("게임 캐릭터 코드"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 유형"),
                                fieldWithPath("colorId")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 색상 ID"),
                                fieldWithPath("assetKey")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 에셋 키"),
                                fieldWithPath("gameCharacterName")
                                        .type(JsonFieldType.STRING)
                                        .description("게임 캐릭터 이름"),
                                fieldWithPath("nickname")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 닉네임"),
                                fieldWithPath("displayName")
                                        .type(JsonFieldType.STRING)
                                        .description("표시 이름"),
                                fieldWithPath("totalXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("총 경험치"),
                                fieldWithPath("level")
                                        .type(JsonFieldType.NUMBER)
                                        .description("레벨"),
                                fieldWithPath("advancementStage")
                                        .type(JsonFieldType.STRING)
                                        .description("전직 단계"),
                                fieldWithPath("representative")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("대표 캐릭터 여부"))));

        verify(characterOnboardingService).createRepresentativeCharacter(
                USER_ID,
                new CreateUserCharacterCommand(1L, "오마", "pistachio")
        );
    }

    @Test
    @DisplayName("게이미피케이션 홈 조회")
    void getsHome() throws Exception {
        // Given: 홈 화면 응답 준비
        DailyQuestResult quest =
                new DailyQuestResult(
                        20L,
                        LocalDate.of(2026, 9, 14),
                        QuestType.ROUTINE,
                        "STUDY",
                        "학습하기",
                        1,
                        1,
                        50L,
                        QuestStatus.COMPLETED);
        given(dailyQuestService.getHome(USER_ID))
                .willReturn(
                        new HomeResult(
                                new CharacterGrowthResult(
                                        10L, "오마", "오마", 120L, 2, 20L, 180L, AdvancementStage.BASE),
                                List.of(quest)));

        // When & Then
        mockMvc.perform(get("/api/v1/gamification/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andDo(document(
                        "gamification/get-home",
                        responseFields(
                                fieldWithPath("growth.nickname")
                                        .type(JsonFieldType.STRING)
                                        .description("캐릭터 닉네임"),
                                fieldWithPath("growth.displayName")
                                        .type(JsonFieldType.STRING)
                                        .description("표시 이름"),
                                fieldWithPath("growth.totalXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("총 경험치"),
                                fieldWithPath("growth.level")
                                        .type(JsonFieldType.NUMBER)
                                        .description("레벨"),
                                fieldWithPath("growth.currentLevelXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("현재 레벨 경험치"),
                                fieldWithPath("growth.nextLevelRequiredXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("다음 레벨 필요 경험치"),
                                fieldWithPath("growth.advancementStage")
                                        .type(JsonFieldType.STRING)
                                        .description("전직 단계"),
                                fieldWithPath("dailyQuests")
                                        .type(JsonFieldType.ARRAY)
                                        .description("일일 퀘스트 목록"),
                                fieldWithPath("dailyQuests[].id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("퀘스트 ID"),
                                fieldWithPath("dailyQuests[].questDate")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 날짜"),
                                fieldWithPath("dailyQuests[].type")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 유형"),
                                fieldWithPath("dailyQuests[].code")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 코드"),
                                fieldWithPath("dailyQuests[].title")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 제목"),
                                fieldWithPath("dailyQuests[].targetCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("목표 횟수"),
                                fieldWithPath("dailyQuests[].progressCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("진행 횟수"),
                                fieldWithPath("dailyQuests[].rewardXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("보상 경험치"),
                                fieldWithPath("dailyQuests[].status")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 상태"))));
    }

    @Test
    @DisplayName("일일 퀘스트 조회")
    void getsDailyQuests() throws Exception {
        // Given: 일일 퀘스트 응답 준비
        given(dailyQuestService.getOrCreateDailyQuests(USER_ID))
                .willReturn(
                        List.of(
                                new DailyQuestResult(
                                        20L,
                                        LocalDate.of(2026, 9, 14),
                                        QuestType.ROUTINE,
                                        "STUDY",
                                        "학습하기",
                                        1,
                                        1,
                                        50L,
                                        QuestStatus.COMPLETED)));

        // When & Then
        mockMvc.perform(get("/api/v1/gamification/quests/daily")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andDo(document(
                        "gamification/get-daily-quests",
                        responseFields(
                                fieldWithPath("[].id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("퀘스트 ID"),
                                fieldWithPath("[].questDate")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 날짜"),
                                fieldWithPath("[].type")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 유형"),
                                fieldWithPath("[].code")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 코드"),
                                fieldWithPath("[].title")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 제목"),
                                fieldWithPath("[].targetCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("목표 횟수"),
                                fieldWithPath("[].progressCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("진행 횟수"),
                                fieldWithPath("[].rewardXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("보상 경험치"),
                                fieldWithPath("[].status")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 상태"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("성장 진행도 조회")
    void getsProgression() throws Exception {
        // Given: 성장 진행도 응답 준비
        given(
                        gamificationProgressionService.getProgression(
                                USER_ID, 10L, LocalDate.of(2026, 9, 14)))
                .willReturn(
                        new GamificationProgressionResult(
                                LocalDate.of(2026, 9, 14),
                                new StudyProgressionState(7200L, false, false, false),
                                new WeekdayStreakState(2, true)));

        // When & Then
        mockMvc.perform(get("/api/v1/gamification/progression")
                        .queryParam("cohortId", "10")
                        .queryParam("aggregationDate", "2026-09-14")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andDo(document(
                        "gamification/get-progression",
                        queryParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("aggregationDate")
                                        .optional()
                                        .description("집계 기준일")),
                        responseFields(
                                fieldWithPath("aggregationDate")
                                        .type(JsonFieldType.STRING)
                                        .description("집계 기준일"),
                                fieldWithPath("studySeconds")
                                        .type(JsonFieldType.NUMBER)
                                        .description("학습 시간"),
                                fieldWithPath("reachedFourHours")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("4시간 달성 여부"),
                                fieldWithPath("reachedSixHours")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("6시간 달성 여부"),
                                fieldWithPath("reachedEightHours")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("8시간 달성 여부"),
                                fieldWithPath("currentWeekdayStreakDays")
                                        .type(JsonFieldType.NUMBER)
                                        .description("요일 연속 학습 일수"),
                                fieldWithPath("streakQualified")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("연속 학습 달성 여부"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("퀘스트 보상 수령")
    void claimsQuest() throws Exception {
        // Given: 퀘스트 보상 응답 준비
        given(dailyQuestService.claim(USER_ID, 20L))
                .willReturn(
                        new DailyQuestResult(
                                20L,
                                LocalDate.of(2026, 9, 14),
                                QuestType.ROUTINE,
                                "STUDY",
                                "학습하기",
                                1,
                                1,
                                50L,
                                QuestStatus.CLAIMED));

        // When & Then
        mockMvc.perform(post("/api/v1/gamification/quests/{user-daily-quest-id}/claim", 20L)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()))
                .andDo(document(
                        "gamification/claim-daily-quest",
                        pathParameters(parameterWithName("user-daily-quest-id").description("보상 수령할 일일 퀘스트 ID")),
                        responseFields(
                                fieldWithPath("id")
                                        .type(JsonFieldType.NUMBER)
                                        .description("퀘스트 ID"),
                                fieldWithPath("questDate")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 날짜"),
                                fieldWithPath("type")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 유형"),
                                fieldWithPath("code")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 코드"),
                                fieldWithPath("title")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 제목"),
                                fieldWithPath("targetCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("목표 횟수"),
                                fieldWithPath("progressCount")
                                        .type(JsonFieldType.NUMBER)
                                        .description("진행 횟수"),
                                fieldWithPath("rewardXp")
                                        .type(JsonFieldType.NUMBER)
                                        .description("보상 경험치"),
                                fieldWithPath("status")
                                        .type(JsonFieldType.STRING)
                                        .description("퀘스트 상태"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("클라이언트는 게이미피케이션 이벤트를 직접 발생시킬 수 없다")
    void doesNotExposeGamificationEventEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/gamification/events/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/gamification/quests/actions/character-checked")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/gamification/quests/actions/routine-reviewed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(dailyQuestService);
    }
}
