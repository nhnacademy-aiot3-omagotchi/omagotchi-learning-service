package site.omagotchi.learningservice.sensor.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@WebMvcTest(SensorDeviceController.class)
@LearningRestDocsTest
class SensorDeviceDocumentationTest {
    private static final Long COHORT_ID = 10L;
    private static final UUID USER_ID = UUID.fromString(TestJwtKeyConfig.USER_ID);
    private static final String EUI = "0011223344556677";
    private static final SensorDeviceResult DEVICE = new SensorDeviceResult(EUI, 20L, "온도 센서", "TH-01", "창가", 60, true);

    private static final String AUTHORIZATION = "Bearer " + TestJwtKeyConfig.issue();

    @MockitoBean
    private SensorDeviceService sensorDeviceService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("센서 장치 목록 조회")
    void listsSensorDevices() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(sensorDeviceService.findAll(COHORT_ID, USER_ID)).willReturn(List.of(DEVICE));

        // When & Then
        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/sensors", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/device-list",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        responseFields(listDeviceFields())))
                .andExpect(jsonPath("$[0].deviceEui").value(EUI));
    }

    @Test
    @DisplayName("센서 장치 생성")
    void createsSensorDevice() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(
                        sensorDeviceService.create(
                                ArgumentMatchers.eq(COHORT_ID),
                                ArgumentMatchers.eq(USER_ID),
                                ArgumentMatchers.any()))
                .willReturn(EUI);

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/sensors", COHORT_ID)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content(
                                "{\"deviceEui\":\"0011223344556677\",\"spaceId\":20,\"model\":\"TH-01\",\"displayName\":\"온도 센서\",\"installationPoint\":\"창가\",\"expectedIntervalSeconds\":60,\"installedAt\":\"2026-08-30T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andDo(document(
                        "sensors/device-create",
                        pathParameters(parameterWithName("cohortId").description("기수 ID")),
                        requestFields(createFields()),
                        responseFields(
                                fieldWithPath("deviceEui")
                                        .type(JsonFieldType.STRING)
                                        .description("생성된 센서 EUI"))))
                .andExpect(jsonPath("$.deviceEui").value(EUI));
    }

    @Test
    @DisplayName("센서 장치 수정")
    void updatesSensorDevice() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(
                        sensorDeviceService.update(
                                ArgumentMatchers.eq(COHORT_ID),
                                ArgumentMatchers.eq(USER_ID),
                                ArgumentMatchers.eq(EUI),
                                ArgumentMatchers.any()))
                .willReturn(DEVICE);

        // When & Then
        mockMvc.perform(put("/api/v1/cohorts/{cohortId}/sensors/{deviceEui}", COHORT_ID, EUI)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content(
                                "{\"spaceId\":20,\"displayName\":\"온도 센서\",\"installationPoint\":\"창가\",\"expectedIntervalSeconds\":60}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/device-update",
                        pathParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("deviceEui").description("센서 EUI")),
                        requestFields(updateFields()),
                        responseFields(
                                deviceFields()[0],
                                deviceFields()[1],
                                deviceFields()[2],
                                deviceFields()[3],
                                deviceFields()[4],
                                deviceFields()[5],
                                deviceFields()[6])))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("센서 활성 상태 변경")
    void changesSensorActiveState() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(sensorDeviceService.changeActive(COHORT_ID, USER_ID, EUI, false))
                .willReturn(new SensorDeviceResult(EUI, 20L, "온도 센서", "TH-01", "창가", 60, false));

        // When & Then
        mockMvc.perform(patch(
                                "/api/v1/cohorts/{cohortId}/sensors/{deviceEui}/active",
                                COHORT_ID,
                                EUI)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/device-active",
                        pathParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("deviceEui").description("센서 EUI")),
                        requestFields(fieldWithPath("active").type(JsonFieldType.BOOLEAN).description("활성화 여부")),
                        responseFields(deviceFields())))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("센서 공간 배정")
    void claimsSensorDevice() throws Exception {
        // Given: 요청 조건과 서비스 응답 fixture 준비
        given(sensorDeviceService.claim(COHORT_ID, USER_ID, EUI, 20L)).willReturn(DEVICE);

        // When & Then
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/sensors/{deviceEui}/claim", COHORT_ID, EUI)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType("application/json")
                        .content("{\"spaceId\":20}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "sensors/device-claim",
                        pathParameters(
                                parameterWithName("cohortId").description("기수 ID"),
                                parameterWithName("deviceEui").description("인계할 센서 EUI")),
                        requestFields(fieldWithPath("spaceId").type(JsonFieldType.NUMBER).description("배치할 공간 ID")),
                        responseFields(deviceFields())))
                .andExpect(jsonPath("$.spaceId").value(20));
    }

    private FieldDescriptor[] listDeviceFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].deviceEui").type(JsonFieldType.STRING).description("센서 EUI"),
            fieldWithPath("[].spaceId").type(JsonFieldType.NUMBER).description("공간 ID"),
            fieldWithPath("[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("[].model").type(JsonFieldType.STRING).description("모델"),
            fieldWithPath("[].installationPoint")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("설치 위치"),
            fieldWithPath("[].expectedIntervalSeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("예상 수집 주기 (초)"),
            fieldWithPath("[].active").type(JsonFieldType.BOOLEAN).description("활성 상태")
        };
    }

    private FieldDescriptor[] deviceFields() {
        return new FieldDescriptor[] {
            fieldWithPath("deviceEui").type(JsonFieldType.STRING).description("센서 EUI"),
            fieldWithPath("spaceId").type(JsonFieldType.NUMBER).description("공간 ID"),
            fieldWithPath("displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("model").type(JsonFieldType.STRING).description("모델"),
            fieldWithPath("installationPoint")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("설치 위치"),
            fieldWithPath("expectedIntervalSeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("예상 수집 주기 (초)"),
            fieldWithPath("active").type(JsonFieldType.BOOLEAN).description("활성 상태")
        };
    }

    private FieldDescriptor[] createFields() {
        return new FieldDescriptor[] {
            fieldWithPath("deviceEui").type(JsonFieldType.STRING).description("센서 EUI"),
            fieldWithPath("spaceId").type(JsonFieldType.NUMBER).description("공간 ID"),
            fieldWithPath("model").type(JsonFieldType.STRING).description("모델"),
            fieldWithPath("displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("installationPoint")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("설치 위치"),
            fieldWithPath("expectedIntervalSeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("수집 주기 (초)"),
            fieldWithPath("installedAt").type(JsonFieldType.STRING).optional().description("설치 시각")
        };
    }

    private FieldDescriptor[] updateFields() {
        return new FieldDescriptor[] {
            fieldWithPath("spaceId").type(JsonFieldType.NUMBER).description("공간 ID"),
            fieldWithPath("displayName").type(JsonFieldType.STRING).optional().description("표시 이름"),
            fieldWithPath("installationPoint")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("설치 위치"),
            fieldWithPath("expectedIntervalSeconds")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("수집 주기 (초)"),
            fieldWithPath("installedAt").type(JsonFieldType.STRING).optional().description("설치 시각")
        };
    }
}
