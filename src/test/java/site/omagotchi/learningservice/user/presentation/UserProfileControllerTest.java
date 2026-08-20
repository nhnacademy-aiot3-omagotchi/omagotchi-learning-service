package site.omagotchi.learningservice.user.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.user.application.UserProfileService;
import site.omagotchi.learningservice.user.application.result.UserNicknameResult;
import site.omagotchi.learningservice.user.application.result.UserProfileResult;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserProfileController.class)
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
@DisplayName("내 프로필 API")
class UserProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final UUID SPOOFED_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @Test
    @DisplayName("프로필 조회는 JWT subject를 현재 사용자로 사용한다")
    void getsProfileWithJwtSubject() throws Exception {
        given(userProfileService.getMyProfile(USER_ID))
                .willReturn(new UserProfileResult("오마", 0L, 0L, 0, null, null));

        mockMvc.perform(get("/api/v1/user-profiles/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .header("X-User-Id", SPOOFED_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("오마"))
                .andDo(document("user-profile/get-my-profile"));

        verify(userProfileService).getMyProfile(USER_ID);
    }

    @Test
    @DisplayName("닉네임 변경은 JWT subject를 현재 사용자로 사용한다")
    void updatesNicknameWithJwtSubject() throws Exception {
        given(userProfileService.updateNickname(USER_ID, "새이름"))
                .willReturn(new UserNicknameResult("새이름"));

        mockMvc.perform(patch("/api/v1/user-profiles/me/nickname")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeyConfig.issue())
                        .header("X-User-Id", SPOOFED_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새이름"));

        verify(userProfileService).updateNickname(USER_ID, "새이름");
    }
}
