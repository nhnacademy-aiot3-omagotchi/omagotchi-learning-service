package site.omagotchi.learningservice.rule.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.rule.application.ThresholdRuleService;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.application.RuleErrorCode;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("임계치 룰 API")
@ExtendWith(MockitoExtension.class)
class ThresholdRuleControllerTest {

    private static final String BASE_PATH = "/api/v1/rule";
    private static final String DEVICE_EUI = "0011223344556677";
    private static final String METRIC = "co2";
    private static final Double THRESHOLD = 1_000.0;
    private static final Long RULE_ID = 1L;
    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REQUEST_ID = "req-0001";

    @Mock
    private ThresholdRuleService thresholdRuleService;

    @InjectMocks
    private ThresholdRuleController thresholdRuleController;

    private MockMvc mockMvc;

    /** 컨트롤러가 JwtAuthenticationToken 을 직접 받으므로 요청마다 principal 로 넣어준다. */
    private final JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            Jwt.withTokenValue("test-token")
                    .header("alg", "RS256")
                    .subject(USER_ID.toString())
                    .claim("role", "USER")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(thresholdRuleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("생성 - 201 과 룰 식별자를 반환하고 인증 주체·요청 식별자를 커맨드에 전달한다")
    void creates() throws Exception {
        given(thresholdRuleService.create(any(CreateThresholdRuleCommand.class)))
                .willReturn(RULE_ID);

        mockMvc.perform(post(BASE_PATH)
                        .principal(authentication)
                        .header("X-Request-ID", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceEui":"0011223344556677","metric":"co2",
                                 "operator":"GT","threshold":1000.0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value(RULE_ID));

        // 커맨드는 record 라 값이 모두 같으면 동등하다 — 인자를 그대로 적어 검증한다
        verify(thresholdRuleService).create(new CreateThresholdRuleCommand(
                DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, USER_ID, REQUEST_ID));
    }

    @Test
    @DisplayName("생성 - X-Request-ID 헤더가 없어도 처리한다")
    void createsWithoutRequestId() throws Exception {
        given(thresholdRuleService.create(any(CreateThresholdRuleCommand.class)))
                .willReturn(RULE_ID);

        mockMvc.perform(post(BASE_PATH)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceEui":"0011223344556677","metric":"co2",
                                 "operator":"GT","threshold":1000.0}
                                """))
                .andExpect(status().isCreated());

        verify(thresholdRuleService).create(new CreateThresholdRuleCommand(
                DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, USER_ID, null));
    }

    @Test
    @DisplayName("생성 - 필수 값 누락, 공백 섞인 장치 EUI, 정의되지 않은 연산자는 400 을 반환한다")
    void rejectsInvalidCreate() throws Exception {
        assertAll(
                // 장치 EUI 누락
                () -> mockMvc.perform(post(BASE_PATH)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"deviceEui":"","metric":"co2",
                                         "operator":"GT","threshold":1000.0}
                                        """))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST")),

                // 장치 EUI 에 공백 — @Pattern 위반
                () -> mockMvc.perform(post(BASE_PATH)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"deviceEui":"AA BB","metric":"co2",
                                         "operator":"GT","threshold":1000.0}
                                        """))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST")),

                // 임계값 누락
                () -> mockMvc.perform(post(BASE_PATH)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"deviceEui":"0011223344556677","metric":"co2",
                                         "operator":"GT"}
                                        """))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST")),

                // 정의되지 않은 연산자 — 역직렬화 단계에서 실패한다
                () -> mockMvc.perform(post(BASE_PATH)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"deviceEui":"0011223344556677","metric":"co2",
                                         "operator":"EQ","threshold":1000.0}
                                        """))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COMMON_MALFORMED_REQUEST"))
        );
    }

    @Test
    @DisplayName("수정 - 변경 여부와 룰 버전을 그대로 반환한다")
    void updates() throws Exception {
        given(thresholdRuleService.update(any(UpdateThresholdRuleCommand.class)))
                .willReturn(new UpdateThresholdRuleResult(true, 1L));

        mockMvc.perform(patch(BASE_PATH + "/{ruleId}", RULE_ID)
                        .principal(authentication)
                        .header("X-Request-ID", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseVersion":0,"operator":"GTE","threshold":900.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.ruleVersion").value(1));

        verify(thresholdRuleService).update(new UpdateThresholdRuleCommand(
                RULE_ID, 0L, Operator.GTE, 900.0, USER_ID, REQUEST_ID));
    }

    @Test
    @DisplayName("수정 - 변경이 없으면 changed=false 를 그대로 내려준다")
    void updatesUnchanged() throws Exception {
        given(thresholdRuleService.update(any(UpdateThresholdRuleCommand.class)))
                .willReturn(new UpdateThresholdRuleResult(false, 3L));

        mockMvc.perform(patch(BASE_PATH + "/{ruleId}", RULE_ID)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseVersion":3,"operator":"GT","threshold":1000.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.ruleVersion").value(3));
    }

    @Test
    @DisplayName("수정 - 기대 버전 누락과 잘못된 경로 변수는 400 을 반환한다")
    void rejectsInvalidUpdate() throws Exception {
        assertAll(
                () -> mockMvc.perform(patch(BASE_PATH + "/{ruleId}", RULE_ID)
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"operator":"GTE","threshold":900.0}
                                        """))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST")),

                () -> mockMvc.perform(patch(BASE_PATH + "/{ruleId}", "abc")
                                .principal(authentication)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"baseVersion":0,"operator":"GTE","threshold":900.0}
                                        """))
                        .andExpect(status().isBadRequest())
        );
    }

    @Test
    @DisplayName("조회 - 룰 목록을 응답 형식으로 변환하며 없으면 빈 배열을 반환한다")
    void findsAll() throws Exception {
        ThresholdRule rule = ThresholdRule.create(
                DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, USER_ID);
        given(thresholdRuleService.readAll()).willReturn(List.of(rule), List.of());

        mockMvc.perform(get(BASE_PATH).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].deviceEui").value(DEVICE_EUI))
                .andExpect(jsonPath("$[0].metric").value(METRIC))
                .andExpect(jsonPath("$[0].operator").value("GT"))
                .andExpect(jsonPath("$[0].threshold").value(THRESHOLD));

        mockMvc.perform(get(BASE_PATH).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "DEVICE_NOT_FOUND, 404",
            "RULE_NOT_FOUND, 404",
            "RULE_ALREADY_EXISTS, 409",
            "RULE_VERSION_CONFLICT, 409",
            "RULE_INVALID_CONDITION, 400"
    })
    @DisplayName("에러코드가 대응하는 HTTP 상태와 코드로 내려간다")
    void mapsErrorStatus(RuleErrorCode errorCode, int expectedStatus) throws Exception {
        given(thresholdRuleService.create(any(CreateThresholdRuleCommand.class)))
                .willThrow(new BusinessException(errorCode));

        mockMvc.perform(post(BASE_PATH)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceEui":"0011223344556677","metric":"co2",
                                 "operator":"GT","threshold":1000.0}
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.message").value(errorCode.message()));
    }

    @Test
    @DisplayName("에러 응답에 SQL 이나 제약 이름 같은 진단 정보가 노출되지 않는다")
    void hidesDiagnostics() throws Exception {
        given(thresholdRuleService.create(any(CreateThresholdRuleCommand.class)))
                .willThrow(new BusinessException(
                        RuleErrorCode.RULE_ALREADY_EXISTS,
                        "duplicate key value violates unique constraint "
                                + "\"uq_threshold_rules_device_metric\""));

        String body = mockMvc.perform(post(BASE_PATH)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceEui":"0011223344556677","metric":"co2",
                                 "operator":"GT","threshold":1000.0}
                                """))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertAll(
                () -> assertFalse(body.contains("uq_threshold_rules_device_metric")),
                () -> assertFalse(body.contains("unique constraint")),
                () -> assertTrue(body.contains(RuleErrorCode.RULE_ALREADY_EXISTS.message()))
        );
    }
}
