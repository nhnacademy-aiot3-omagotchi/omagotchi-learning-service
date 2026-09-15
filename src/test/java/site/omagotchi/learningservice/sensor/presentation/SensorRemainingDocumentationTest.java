package site.omagotchi.learningservice.sensor.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.config.PasswordEncoderConfig;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.global.security.basic.ServiceCredentialAuthenticationProviderFactory;
import site.omagotchi.learningservice.global.security.rule.RuleCredentialProperties;
import site.omagotchi.learningservice.global.security.rule.RuleSecurityConfig;
import site.omagotchi.learningservice.sensor.application.SensorSeriesService;
import site.omagotchi.learningservice.sensor.application.SpaceEnvironmentService;
import site.omagotchi.learningservice.sensor.application.ThresholdRuleService;
import site.omagotchi.learningservice.sensor.application.result.ApplySpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.application.result.SpaceEnvironmentResult;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(
        controllers = {
            SensorSeriesController.class,
            SpaceEnvironmentController.class,
            InternalThresholdRuleController.class,
            ThresholdRuleController.class
        })
@Import({
    RuleSecurityConfig.class,
    PasswordEncoderConfig.class,
    ServiceCredentialAuthenticationProviderFactory.class
})
@EnableConfigurationProperties(RuleCredentialProperties.class)
@LearningRestDocsTest
class SensorRemainingDocumentationTest {
    private static final Long COHORT = 10L;
    private static final Long SPACE = 20L;
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String AUTHORIZATION =
            "Bearer "
                    + TestJwtKeyConfig.issue(
                            TestJwtKeyConfig.ISSUER,
                            TestJwtKeyConfig.AUDIENCE,
                            USER.toString(),
                            "USER");
    private static final String RULE_AUTHORIZATION =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString(
                                    "rule-service:test-only-rule-learning-password"
                                            .getBytes(StandardCharsets.UTF_8));

    @MockitoBean
    private SensorSeriesService seriesService;

    @MockitoBean
    private SpaceEnvironmentService environmentService;

    @MockitoBean
    private ThresholdRuleService ruleService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공간 센서 시계열 조회")
    void readsSpaceSeries() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        SpaceSeries result =
                new SpaceSeries(
                        "Lab",
                        "temperature",
                        SeriesWindow.DAY,
                        NOW.minusSeconds(3600),
                        NOW,
                        List.of(new SensorRef("0011", "p1", "센서")),
                        List.of(
                                new SpaceSeriesPoint(
                                        NOW.minusSeconds(3600),
                                        22.5,
                                        21.0,
                                        "0011",
                                        24.0,
                                        "0011",
                                        2,
                                        true)));
        given(seriesService.getSpaceSeries(COHORT, USER, "Lab", "temperature", "day"))
                .willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/sensors/space-series", COHORT)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("location", "Lab")
                        .param("measurement", "temperature")
                        .param("window", "day"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/series",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        queryParameters(
                                parameterWithName("location").description("공간 위치"),
                                parameterWithName("measurement").description("측정 항목"),
                                parameterWithName("window")
                                        .description("조회 창 (day/week/month)")),
                        responseFields(seriesFields())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorCount").value(1));
    }

    @Test
    @DisplayName("공간 환경 조회")
    void readsSpaceEnvironment() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(environmentService.getCohortEnvironments(COHORT, USER))
                .willReturn(List.of(new SpaceEnvironmentResult(SPACE, 450.0, 22.5, 45.0, NOW, 2)));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/sensors/environment", COHORT)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/environment",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        responseFields(environmentFields())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].co2").value(450.0));
    }

    @Test
    @DisplayName("내부 임계값 규칙 조회")
    void readsInternalRules() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        ThresholdRule rule = mock(ThresholdRule.class);
        given(rule.getId()).willReturn(1L);
        given(rule.getDeviceEui()).willReturn("0011");
        given(rule.getMetric()).willReturn("temperature");
        given(rule.getOperator()).willReturn(Operator.GT);
        given(rule.getThreshold()).willReturn(30.0);
        given(rule.getVersion()).willReturn(2L);
        given(ruleService.readAllForRuleEngine()).willReturn(List.of(rule));

        // When & Then
        mockMvc.perform(get("/api/v1/internal/threshold-rules")
                        .header(HttpHeaders.AUTHORIZATION, RULE_AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document("sensors/internal-threshold-rules", responseFields(ruleFields())))
                .andExpect(jsonPath("$[0].ruleVersion").value(2));
    }

    @Test
    @DisplayName("임계값 규칙 생성")
    void createsThresholdRule() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(ruleService.create(eq(COHORT), eq(USER), anyString(), any())).willReturn(7L);

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/threshold-rules", COHORT)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content(
                                "{\"deviceEui\":\"0011\",\"metric\":\"temperature\",\"operator\":\"GT\",\"threshold\":30.0}"))
                .andExpect(status().isCreated())
                .andDo(document(
                        "sensors/threshold-rule-create",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        requestFields(
                                fieldWithPath("deviceEui").description("센서 EUI"),
                                fieldWithPath("metric").description("측정 항목"),
                                fieldWithPath("operator").description("비교 연산자"),
                                fieldWithPath("threshold").description("임계값")),
                        responseFields(fieldWithPath("ruleId").type(JsonFieldType.NUMBER).description("생성된 룰 ID"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value(7));
    }

    @Test
    @DisplayName("임계값 규칙 수정")
    void updatesThresholdRule() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(ruleService.update(eq(COHORT), eq(USER), anyString(), eq(7L), any()))
                .willReturn(new UpdateThresholdRuleResult(true, 3L));

        // When & Then
        mockMvc.perform(patch("/api/v1/cohorts/{cohortId}/threshold-rules/{ruleId}", COHORT, 7L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content("{\"baseVersion\":2,\"operator\":\"GTE\",\"threshold\":31.0}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/threshold-rule-update",
                        pathParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("ruleId").description("룰 ID")),
                        requestFields(
                                fieldWithPath("baseVersion").description("수정 기준 버전"),
                                fieldWithPath("operator").description("비교 연산자"),
                                fieldWithPath("threshold").description("임계값")),
                        responseFields(
                                fieldWithPath("changed")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("실제 변경 여부"),
                                fieldWithPath("ruleVersion")
                                        .type(JsonFieldType.NUMBER)
                                        .description("현재 룰 버전"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    @DisplayName("임계값 규칙 목록 조회")
    void listsThresholdRules() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        ThresholdRule rule = mock(ThresholdRule.class);
        given(rule.getId()).willReturn(1L);
        given(rule.getDeviceEui()).willReturn("0011");
        given(rule.getMetric()).willReturn("co2");
        given(rule.getOperator()).willReturn(Operator.LTE);
        given(rule.getThreshold()).willReturn(800.0);
        given(rule.getVersion()).willReturn(1L);
        given(ruleService.findAllByCohort(COHORT, USER)).willReturn(List.of(rule));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/threshold-rules", COHORT)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/threshold-rule-list",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        responseFields(ruleFields())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공간 임계값 목록 조회")
    void listsSpaceThresholds() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        SpaceThresholdResult.MetricThresholdResult metric =
                new SpaceThresholdResult.MetricThresholdResult(
                        "co2", Operator.LTE, 800.0, 2, false);
        given(ruleService.findAllBySpace(COHORT, USER))
                .willReturn(List.of(new SpaceThresholdResult(SPACE, 2, List.of(metric))));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/threshold-rules/spaces", COHORT)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/space-threshold-list",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        responseFields(spaceThresholdFields())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공간 임계값 적용")
    void appliesSpaceThresholds() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(ruleService.applyToSpace(eq(COHORT), eq(USER), anyString(), eq(SPACE), any()))
                .willReturn(new ApplySpaceThresholdResult(SPACE, 2, 2, 1, 0, 0));

        // When & Then
        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohortId}/threshold-rules/spaces/{spaceId}",
                                COHORT,
                                SPACE)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content(
                                "{\"rules\":[{\"metric\":\"co2\",\"operator\":\"LTE\",\"threshold\":800},{\"metric\":\"temperature\",\"operator\":\"GTE\",\"threshold\":18},{\"metric\":\"humidity\",\"operator\":\"LTE\",\"threshold\":60}]}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/space-threshold-apply",
                        pathParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("spaceId").description("공간 ID")),
                        requestFields(
                                fieldWithPath("rules").description("세 가지 metric 조건"),
                                fieldWithPath("rules[].metric").description("측정 항목"),
                                fieldWithPath("rules[].operator").description("비교 연산자"),
                                fieldWithPath("rules[].threshold").description("임계값")),
                        responseFields(
                                fieldWithPath("spaceId").description("공간 ID"),
                                fieldWithPath("deviceCount").description("대상 기기 수"),
                                fieldWithPath("created").description("생성 룰 수"),
                                fieldWithPath("applied").description("변경 룰 수"),
                                fieldWithPath("unchanged").description("변경 없는 룰 수"),
                                fieldWithPath("missing").description("누락 룰 수"))))
                .andExpect(status().isOk());
    }

    private FieldDescriptor[] seriesFields() {
        return new FieldDescriptor[] {
            fieldWithPath("location").description("공간"),
                    fieldWithPath("measurement").description("측정 항목"),
            fieldWithPath("window").description("조회 창"),
                    fieldWithPath("interval").description("집계 간격"),
            fieldWithPath("from").description("시작 시각"), fieldWithPath("to").description("종료 시각"),
            fieldWithPath("serverTime").description("서버 시각"),
                    fieldWithPath("sources.settled").description("확정 bucket"),
            fieldWithPath("sources.hot").description("진행 bucket"),
                    fieldWithPath("sensorCount").description("센서 수"),
            fieldWithPath("sensors").description("센서 목록"),
                    fieldWithPath("sensors[].deviceEui").description("센서 EUI"),
            fieldWithPath("sensors[].point").description("InfluxDB 포인트"),
                    fieldWithPath("sensors[].displayName").description("표시 이름"),
            fieldWithPath("points").description("시계열 점"),
                    fieldWithPath("points[].time").description("시각"),
            fieldWithPath("points[].avg").description("평균"),
                    fieldWithPath("points[].min").description("최솟값"),
            fieldWithPath("points[].minDeviceEui").description("최솟값 센서"),
                    fieldWithPath("points[].max").description("최댓값"),
            fieldWithPath("points[].maxDeviceEui").description("최댓값 센서"),
                    fieldWithPath("points[].count").description("측정 수"),
            fieldWithPath("points[].partial")
                            .type(JsonFieldType.BOOLEAN)
                            .optional()
                            .description("진행 중 점 여부")
        };
    }

    private FieldDescriptor[] environmentFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].spaceId").description("공간 ID"),
            fieldWithPath("[].co2").optional().description("CO2"),
            fieldWithPath("[].temperature").optional().description("온도"),
            fieldWithPath("[].humidity").optional().description("습도"),
            fieldWithPath("[].measuredAt").optional().description("측정 시각"),
            fieldWithPath("[].deviceCount").description("기기 수")
        };
    }

    private FieldDescriptor[] ruleFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].ruleId").description("룰 ID"),
            fieldWithPath("[].deviceEui").description("센서 EUI"),
            fieldWithPath("[].metric").description("측정 항목"),
            fieldWithPath("[].operator").description("비교 연산자"),
            fieldWithPath("[].threshold").description("임계값"),
            fieldWithPath("[].ruleVersion").description("룰 버전")
        };
    }

    private FieldDescriptor[] spaceThresholdFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].spaceId").description("공간 ID"),
            fieldWithPath("[].deviceCount").description("기기 수"),
            fieldWithPath("[].metrics").description("지표별 임계치"),
            fieldWithPath("[].metrics[].metric").description("측정 항목"),
            fieldWithPath("[].metrics[].operator").description("비교 연산자"),
            fieldWithPath("[].metrics[].threshold").description("임계값"),
            fieldWithPath("[].metrics[].ruleCount").description("룰 수"),
            fieldWithPath("[].metrics[].mixed").description("기기별 값 혼합 여부")
        };
    }
}
