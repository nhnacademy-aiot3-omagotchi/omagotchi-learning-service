package site.omagotchi.learningservice.environment.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;
import site.omagotchi.learningservice.environment.application.SensorEventQueryService;
import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.application.query.SensorEventPage;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.environment.presentation.web.SensorEventController;
import site.omagotchi.learningservice.global.config.ClockConfig;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(controllers = {SensorEventController.class, SimulateController.class})
@Import({ClockConfig.class})
@EnableConfigurationProperties(EnvironmentProperties.class)
@TestPropertySource(
        properties = {"environment.iot.simulator.enabled=true", "environment.iot.secret=secret"})
@LearningRestDocsTest
class EnvironmentDocumentationTest {
    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String AUTHORIZATION =
            "Bearer "
                    + TestJwtKeyConfig.issue(
                            TestJwtKeyConfig.ISSUER,
                            TestJwtKeyConfig.AUDIENCE,
                            USER.toString(),
                            "USER");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SensorEventQueryService queryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Test
    @DisplayName("센서 이벤트 조회")
    void readsSensorEvents() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        SensorEvent event =
                SensorEvent.of(
                        new SensorDetection(
                                "trace-1",
                                SensorEventType.ANOMALY,
                                "실습실",
                                "천장",
                                "device-1",
                                "co2",
                                1200.0,
                                "범위 초과",
                                null,
                                null,
                                NOW.minusSeconds(1),
                                NOW));
        given(
                        queryService.getEvents(
                                10L, USER, SensorEventType.ANOMALY, "device-1", null, null, 0, 20))
                .willReturn(
                        new SensorEventPage(
                                List.of(new SensorEventItem(event, "실습실 센서")), 0, 20, 1, 1));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/sensor-events", 10L)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .queryParam("type", "ANOMALY")
                        .queryParam("deviceEui", "device-1")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andDo(document(
                        "environment/get-sensor-events",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        queryParameters(
                                parameterWithName("type")
                                        .optional()
                                        .description("센서 이벤트 유형"),
                                parameterWithName("deviceEui")
                                        .optional()
                                        .description("센서 EUI"),
                                parameterWithName("from")
                                        .optional()
                                        .description("조회 시작 시각"),
                                parameterWithName("to").optional().description("조회 종료 시각"),
                                parameterWithName("page").optional().description("페이지 번호"),
                                parameterWithName("size").optional().description("페이지 크기")),
                        responseFields(eventFields())));
    }

    @Test
    @DisplayName("IoT 시뮬레이터 명령 실행")
    void executesSimulatorCommand() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        // When & Then
        mockMvc.perform(post("/simulator/iot/{action}", "turn-on")
                        .header("X-iot-TOKEN", "secret")
                        .queryParam("success", "true")
                        .contentType("application/json")
                        .content("{\"spaceId\":\"1\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "environment/simulate-iot",
                        pathParameters(parameterWithName("action").description("시뮬레이터 동작 이름")),
                        requestHeaders(headerWithName("X-iot-TOKEN").description("IoT 공유 토큰")),
                        queryParameters(parameterWithName("success").optional().description("시뮬레이션 성공 여부, 기본값 true")),
                        requestFields(
                                fieldWithPath("spaceId")
                                        .type(JsonFieldType.STRING)
                                        .description("시뮬레이터 명령의 공간 ID")),
                        responseFields(
                                fieldWithPath("actioned")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("동작 성공 여부"),
                                fieldWithPath("at")
                                        .type(JsonFieldType.STRING)
                                        .description("처리 시각"),
                                fieldWithPath("simulated")
                                        .type(JsonFieldType.BOOLEAN)
                                        .description("시뮬레이션 여부"))));
    }

    private FieldDescriptor[] eventFields() {
        return new FieldDescriptor[] {
            fieldWithPath("content").type(JsonFieldType.ARRAY).description("센서 이벤트 목록"),
            fieldWithPath("content[].eventId").type(JsonFieldType.STRING).description("이벤트 ID"),
            fieldWithPath("content[].type").type(JsonFieldType.STRING).description("이벤트 유형"),
            fieldWithPath("content[].traceId").type(JsonFieldType.STRING).description("추적 ID"),
            fieldWithPath("content[].deviceEui").type(JsonFieldType.STRING).description("센서 EUI"),
            fieldWithPath("content[].displayName")
                    .type(JsonFieldType.STRING)
                    .description("센서 표시 이름"),
            fieldWithPath("content[].location").type(JsonFieldType.STRING).description("센서 위치"),
            fieldWithPath("content[].point")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("센서 지점"),
            fieldWithPath("content[].measurement").type(JsonFieldType.STRING).description("측정 항목"),
            fieldWithPath("content[].value").type(JsonFieldType.NUMBER).description("측정값"),
            fieldWithPath("content[].detail").type(JsonFieldType.STRING).description("상세 내용"),
            fieldWithPath("content[].measuredAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("측정 시각"),
            fieldWithPath("content[].receivedAt").type(JsonFieldType.STRING).description("수신 시각"),
            fieldWithPath("content[].action")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("수행 동작"),
            fieldWithPath("content[].actionLabel")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("동작 표시명"),
            fieldWithPath("content[].actionStatus").type(JsonFieldType.STRING).description("동작 상태"),
            fieldWithPath("content[].actionConfirmedAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("동작 확인 시각"),
            fieldWithPath("content[].actionSimulated")
                    .type(JsonFieldType.BOOLEAN)
                    .optional()
                    .description("동작 시뮬레이션 여부"),
            fieldWithPath("content[].actionError")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("동작 오류"),
            fieldWithPath("content[].notifiedAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("알림 발송 시각"),
            fieldWithPath("page").type(JsonFieldType.NUMBER).description("페이지 번호"),
            fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지 크기"),
            fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("전체 이벤트 수"),
            fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
            fieldWithPath("capacity").type(JsonFieldType.NUMBER).description("캐시 용량"),
            fieldWithPath("retention").type(JsonFieldType.STRING).description("캐시 보관 기간")
        };
    }
}
