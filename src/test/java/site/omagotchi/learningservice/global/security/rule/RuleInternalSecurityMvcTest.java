package site.omagotchi.learningservice.global.security.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.config.PasswordEncoderConfig;
import site.omagotchi.learningservice.global.security.JwtAuthorityConfig;
import site.omagotchi.learningservice.global.security.JwtConfig;
import site.omagotchi.learningservice.global.security.JwtProperties;
import site.omagotchi.learningservice.global.security.SecurityErrorResponseHandler;
import site.omagotchi.learningservice.global.security.SecurityConfig;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.learningservice.rule.application.ThresholdRuleService;
import site.omagotchi.learningservice.rule.presentation.InternalThresholdRuleController;
import site.omagotchi.learningservice.rule.presentation.ThresholdRuleController;

import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        InternalThresholdRuleController.class,
        ThresholdRuleController.class
})
@Import({
        RuleSecurityConfig.class,
        SecurityConfig.class,
        SecurityErrorResponseHandler.class,
        JwtConfig.class,
        JwtAuthorityConfig.class,
        TestJwtKeyConfig.class,
        PasswordEncoderConfig.class,
        ServiceCredentialAuthenticationProviderFactory.class
})
@EnableConfigurationProperties({RuleCredentialProperties.class, JwtProperties.class})
@ActiveProfiles("test")
class RuleInternalSecurityMvcTest {

    private static final String USERNAME = "rule-service";
    private static final String PASSWORD = "test-only-rule-learning-password";
    private static final String PATH = "/api/v1/internal/threshold-rules";
    private static final String PUBLIC_PATH = "/api/v1/threshold-rules";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ThresholdRuleService thresholdRuleService;

    @Test
    @DisplayName("Rule 내부 조회는 Rule Credential 필수")
    void requiresRuleCredential() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Basic realm=\"omagotchi-learning-rule\"")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get(PATH).with(httpBasic(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("Rule 내부 경계의 Access JWT 거절")
    void rejectsAccessJwtFromRuleBoundary() throws Exception {
        mockMvc.perform(get(PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwtKeyConfig.issue()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Basic realm=\"omagotchi-learning-rule\"")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("공개 사용자 경계의 Rule Credential 거절")
    void rejectsRuleCredentialFromPublicBoundary() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer")
                ))
                .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("Rule Credential의 임계치 기준 조회 허용")
    void allowsRuleToReadThresholdRules() throws Exception {
        given(thresholdRuleService.readAll()).willReturn(List.of());

        mockMvc.perform(get(PATH).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(thresholdRuleService).readAll();
    }

    @Test
    @DisplayName("Rule Credential의 임계치 기준 변경 거절")
    void deniesRuleWriteRequest() throws Exception {
        mockMvc.perform(post(PATH).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verifyNoInteractions(thresholdRuleService);
    }

    @Test
    @DisplayName("미등록 Rule 내부 하위 경로 거절")
    void deniesUnregisteredRuleSubpath() throws Exception {
        mockMvc.perform(get(PATH + "/unsupported")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));

        verifyNoInteractions(thresholdRuleService);
    }
}
