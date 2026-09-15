package site.omagotchi.learningservice.user.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;
import site.omagotchi.learningservice.user.application.UserProfileService;
import site.omagotchi.learningservice.user.application.result.ApprovedCohortResult;
import site.omagotchi.learningservice.user.application.result.CurrentCharacterResult;
import site.omagotchi.learningservice.user.application.result.UserNicknameResult;
import site.omagotchi.learningservice.user.application.result.UserProfileResult;

@WebMvcTest(controllers = UserProfileController.class)
@DisplayName("내 프로필 API")
@LearningRestDocsTest
class UserProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID SPOOFED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @MockitoBean
    private UserProfileService userProfileService;

    @Test
    @DisplayName("프로필 조회는 JWT subject를 현재 사용자로 사용한다")
    void getsProfileWithJwtSubject() throws Exception {
        given(userProfileService.getMyProfile(USER_ID))
                .willReturn(
                        new UserProfileResult(
                                "테스트사용자",
                                0L,
                                0L,
                                0,
                                new ApprovedCohortResult(
                                        1L,
                                        "테스트 기수",
                                        LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 12, 31),
                                        CohortStatus.ACTIVE,
                                        CohortMembershipRole.STUDENT,
                                        CohortMembershipStatus.ACTIVE),
                                new CurrentCharacterResult(
                                        "테스트사용자",
                                        1,
                                        0L,
                                        100L,
                                        "테스트 캐릭터",
                                        "night",
                                        "pistachio",
                                        "night/pistachio")));

        mockMvc.perform(get("/api/v1/user-profiles/me/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .header("X-User-Id", SPOOFED_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("테스트사용자"))
                .andExpect(jsonPath("$.currentCharacter.type").value("night"))
                .andExpect(jsonPath("$.currentCharacter.colorId").value("pistachio"))
                .andExpect(jsonPath("$.currentCharacter.assetKey").value("night/pistachio"))
                .andDo(document(
                        "user-profile/get-my-profile",
                        responseFields(
                                fieldWithPath("nickname").description("현재 사용자의 닉네임"),
                                fieldWithPath("totalStudySeconds")
                                        .description("누적 학습 시간(초)"),
                                fieldWithPath("completedSessionCount")
                                        .description("완료한 학습 세션 수"),
                                fieldWithPath("attendanceStreakDays")
                                        .description("현재 출석 연속 일수"),
                                fieldWithPath("approvedCohort").description("현재 승인된 기수 정보"),
                                fieldWithPath("approvedCohort.cohortId")
                                        .description("기수 식별자"),
                                fieldWithPath("approvedCohort.name").description("기수 이름"),
                                fieldWithPath("approvedCohort.startDate")
                                        .description("기수 시작일 (`yyyy-MM-dd`)"),
                                fieldWithPath("approvedCohort.endDate")
                                        .description("기수 종료일 (`yyyy-MM-dd`)"),
                                fieldWithPath("approvedCohort.cohortStatus")
                                        .description("기수 상태"),
                                fieldWithPath("approvedCohort.role")
                                        .description("기수 내 사용자 역할"),
                                fieldWithPath("approvedCohort.membershipStatus")
                                        .description("기수 소속 상태"),
                                fieldWithPath("currentCharacter")
                                        .description("현재 대표 캐릭터 정보"),
                                fieldWithPath("currentCharacter.nickname")
                                        .description("대표 캐릭터에 저장된 닉네임"),
                                fieldWithPath("currentCharacter.level")
                                        .description("현재 레벨"),
                                fieldWithPath("currentCharacter.currentExp")
                                        .description("현재 레벨에서 획득한 경험치"),
                                fieldWithPath("currentCharacter.requiredExp")
                                        .description("다음 레벨에 필요한 경험치"),
                                fieldWithPath("currentCharacter.name")
                                        .description("캐릭터 이름"),
                                fieldWithPath("currentCharacter.type")
                                        .description("캐릭터 타입"),
                                fieldWithPath("currentCharacter.colorId")
                                        .description("캐릭터 색상 식별자"),
                                fieldWithPath("currentCharacter.assetKey")
                                        .description("캐릭터 에셋 키"))));

        verify(userProfileService).getMyProfile(USER_ID);
    }

    @Test
    @DisplayName("닉네임 변경은 JWT subject를 현재 사용자로 사용한다")
    void updatesNicknameWithJwtSubject() throws Exception {
        given(userProfileService.updateNickname(USER_ID, "새이름"))
                .willReturn(new UserNicknameResult("새이름"));

        mockMvc.perform(patch("/api/v1/user-profiles/me/nickname")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .header("X-User-Id", SPOOFED_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새이름"))
                .andDo(document(
                        "user-profile/update-nickname",
                        requestFields(fieldWithPath("nickname").description("변경할 닉네임 (2~12자의 한글, 영문, 숫자)")),
                        responseFields(fieldWithPath("nickname").description("변경된 닉네임"))));

        verify(userProfileService).updateNickname(USER_ID, "새이름");
    }

    @Test
    @DisplayName("잘못된 닉네임 변경 시 400 응답")
    void rejectsInvalidNickname() throws Exception {
        // Given: 닉네임 검증 실패 응답 준비
        given(userProfileService.updateNickname(USER_ID, "잘못된 닉네임!"))
                .willThrow(new BusinessException(GamificationErrorCode.INVALID_CHARACTER_NICKNAME));

        // When & Then
        mockMvc.perform(patch("/api/v1/user-profiles/me/nickname")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"잘못된 닉네임!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHARACTER_NICKNAME"))
                .andExpect(jsonPath("$.path").value("/api/v1/user-profiles/me/nickname"))
                .andDo(document(
                        "user-profile/update-nickname-invalid",
                        requestFields(fieldWithPath("nickname").description("검증에 실패한 닉네임")),
                        responseFields(
                                fieldWithPath("code").description("오류 코드"),
                                fieldWithPath("message").description("오류 메시지"),
                                fieldWithPath("path").description("오류가 발생한 요청 경로"),
                                fieldWithPath("requestId")
                                        .description("요청 추적 식별자 (없으면 null)"))));

        verify(userProfileService).updateNickname(USER_ID, "잘못된 닉네임!");
    }
}
