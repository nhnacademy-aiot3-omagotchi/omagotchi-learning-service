package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 점유 API의 HTTP 계약.
 *
 * <p>오류 응답을 컨트롤러가 만들지 않는다는 것이 요점이다 — 서비스가 던진 코드가
 * {@code GlobalExceptionHandler}를 거쳐 상태로 옮겨진다. 여기서 try-catch나
 * ResponseEntity 분기가 생기면 이 테스트가 아니라 설계가 잘못된 것이다.</p>
 */
class RoomOccupancyControllerTest {

    private static final String PATH = "/api/v1/spaces/1/occupancies";
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final OffsetDateTime STARTED_AT =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    private RoomOccupancyService roomOccupancyService;
    private RoomOccupancyLifecycleService roomOccupancyLifecycleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        roomOccupancyService = mock(RoomOccupancyService.class);
        roomOccupancyLifecycleService = mock(RoomOccupancyLifecycleService.class);
        mockMvc = standaloneSetup(new RoomOccupancyController(
                        roomOccupancyService, roomOccupancyLifecycleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("점유에 성공하면 201과 점유 정보를 응답한다.")
    void test1() throws Exception {
        when(roomOccupancyService.start(any(), any())).thenReturn(result());

        mockMvc.perform(post(PATH).header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occupancyId").value(100))
                .andExpect(jsonPath("$.spaceId").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.extensionCount").value(0))
                .andExpect(jsonPath("$.remainingSeconds").value(7200));
    }

    /** 요청 본문이 없는 것이 의도다 — 기수 식별자를 받으면 출근한 기수와 다른 기수로 점유할 수 있다. */
    @Test
    @DisplayName("경로의 공간과 헤더의 계정만으로 서비스를 호출한다.")
    void test2() throws Exception {
        when(roomOccupancyService.start(any(), any())).thenReturn(result());

        mockMvc.perform(post(PATH).header("X-User-Id", USER_ID))
                .andExpect(status().isCreated());

        verify(roomOccupancyService).start(1L, USER_ID);
    }

    @Test
    @DisplayName("응답에 점유자·참여자 정보를 담지 않는다.")
    void test3() throws Exception {
        when(roomOccupancyService.start(any(), any())).thenReturn(result());

        mockMvc.perform(post(PATH).header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occupierUserId").doesNotExist())
                .andExpect(jsonPath("$.occupierMembershipId").doesNotExist())
                .andExpect(jsonPath("$.participants").doesNotExist());
    }

    @Test
    @DisplayName("사용 중인 회의실이면 409와 공실 알림 안내를 응답한다.")
    void test4() throws Exception {
        assertErrorResponse(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED, 409);
    }

    @Test
    @DisplayName("재실이 아니면 403을 응답한다.")
    void test5() throws Exception {
        assertErrorResponse(OccupancyErrorCode.NOT_PRESENT, 403);
    }

    @Test
    @DisplayName("회의실이 아니면 400을 응답한다.")
    void test6() throws Exception {
        assertErrorResponse(OccupancyErrorCode.NOT_MEETING_ROOM, 400);
    }

    @Test
    @DisplayName("없는 공간이면 404를 응답한다.")
    void test7() throws Exception {
        assertErrorResponse(OccupancyErrorCode.SPACE_NOT_FOUND, 404);
    }

    /**
     * 재실 조회 실패는 클라이언트가 분기할 계약이 없는 기술 실패다. BusinessException으로
     * 감싸지 않으므로 마지막 경계에서 500이 된다.
     */
    @Test
    @DisplayName("출결 모듈 조회가 실패하면 500을 응답한다.")
    void test8() throws Exception {
        when(roomOccupancyService.start(any(), any()))
                .thenThrow(new IllegalStateException("출결 모듈 조회 실패"));

        mockMvc.perform(post(PATH).header("X-User-Id", USER_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_SERVER_ERROR"));
    }

    // ────────────────────────────── 연장·반납 ──────────────────────────────

    /**
     * 상태 전이라 경로 마지막이 동사형이고, 200에 본문을 싣는다 — 클라이언트가 새
     * 만료 시각으로 타이머를 다시 맞춰야 한다.
     */
    @Test
    @DisplayName("연장에 성공하면 200과 갱신된 만료 시각을 응답한다.")
    void test9() throws Exception {
        when(roomOccupancyLifecycleService.extend(any(), any())).thenReturn(extendedResult());

        mockMvc.perform(post(PATH + "/extend").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensionCount").value(1))
                .andExpect(jsonPath("$.remainingSeconds").value(1800));

        verify(roomOccupancyLifecycleService).extend(1L, USER_ID);
    }

    @Test
    @DisplayName("만료 30분 전이 되기 전에 연장하면 409를 응답한다.")
    void test10() throws Exception {
        when(roomOccupancyLifecycleService.extend(any(), any()))
                .thenThrow(new BusinessException(OccupancyErrorCode.EXTENSION_TOO_EARLY));

        mockMvc.perform(post(PATH + "/extend").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_EXTENSION_TOO_EARLY"));
    }

    @Test
    @DisplayName("연장 횟수를 다 쓰면 409를 응답한다.")
    void test11() throws Exception {
        when(roomOccupancyLifecycleService.extend(any(), any()))
                .thenThrow(new BusinessException(OccupancyErrorCode.EXTENSION_LIMIT_EXCEEDED));

        mockMvc.perform(post(PATH + "/extend").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_EXTENSION_LIMIT_EXCEEDED"));
    }

    /** DELETE가 아닌 이유는 점유 행이 이력으로 보존되기 때문이다 — 제거가 아니라 상태 전이다. */
    @Test
    @DisplayName("반납에 성공하면 204를 응답한다.")
    void test12() throws Exception {
        mockMvc.perform(post(PATH + "/release").header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());

        verify(roomOccupancyLifecycleService).release(1L, USER_ID);
    }

    @Test
    @DisplayName("점유자가 아니면 반납할 수 없다.")
    void test13() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.NOT_OCCUPIER))
                .when(roomOccupancyLifecycleService).release(any(), any());

        mockMvc.perform(post(PATH + "/release").header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_NOT_OCCUPIER"));
    }

    @Test
    @DisplayName("이미 종료된 점유를 반납하면 409를 응답한다.")
    void test14() throws Exception {
        doThrow(new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED))
                .when(roomOccupancyLifecycleService).release(any(), any());

        mockMvc.perform(post(PATH + "/release").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_ENDED"));
    }

    private RoomOccupancyResult extendedResult() {
        return new RoomOccupancyResult(
                100L,
                1L,
                OccupancyStatus.ACTIVE,
                STARTED_AT,
                STARTED_AT.plusHours(2).plusMinutes(30),
                1,
                1800L
        );
    }

    private void assertErrorResponse(ErrorCode errorCode, int expectedStatus) throws Exception {
        when(roomOccupancyService.start(any(), any()))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post(PATH).header("X-User-Id", USER_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.code()))
                .andExpect(jsonPath("$.message").value(errorCode.message()))
                .andExpect(jsonPath("$.path").value(PATH));
    }

    private RoomOccupancyResult result() {
        return new RoomOccupancyResult(
                100L,
                1L,
                OccupancyStatus.ACTIVE,
                STARTED_AT,
                STARTED_AT.plusHours(2),
                0,
                7200L
        );
    }
}
