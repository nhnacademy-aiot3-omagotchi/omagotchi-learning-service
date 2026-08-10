package site.omagotchi.learningservice.occupancy.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 점유 시작 흐름 (MR-01, 08, 09, 10, 20, 22, 27 / RM-13).
 *
 * <p>동시 요청은 여기서 검증하지 않는다. 부분 유니크가 최종 방어선이므로 실제 DB가
 * 있어야 하고, 그것은 Testcontainers 통합 테스트의 몫이다. 이 테스트가 고정하는 것은
 * 락 밖·락 안의 순서와 단일 요청에서의 판정이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RoomOccupancyServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    private static final Long SPACE_ID = 1L;
    private static final Long OTHER_SPACE_ID = 2L;
    private static final Long MEMBERSHIP_ID = 10L;
    private static final Long OCCUPANCY_ID = 100L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private SpaceReader spaceReader;

    @Mock
    private AttendancePresenceQueryService attendancePresenceQueryService;

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    @Mock
    private OccupancyEventPublisher eventPublisher;

    private Clock clock;
    private RoomOccupancyService roomOccupancyService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        roomOccupancyService = new RoomOccupancyService(
                spaceReader,
                attendancePresenceQueryService,
                occupancyRepository,
                participantRepository,
                eventPublisher,
                clock
        );
    }

    @Test
    @DisplayName("점유에 성공하면 만료 시각은 시작 시각의 2시간 뒤다.")
    void test1() {
        givenLockedRoom();
        givenSavedOccupancy();

        RoomOccupancyResult result = roomOccupancyService.start(SPACE_ID, USER_ID);

        assertThat(result.occupancyId()).isEqualTo(OCCUPANCY_ID);
        assertThat(result.spaceId()).isEqualTo(SPACE_ID);
        assertThat(result.status()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(result.startedAt()).isEqualTo(now());
        assertThat(result.expiresAt()).isEqualTo(now().plusHours(2));
        assertThat(result.extensionCount()).isZero();
        assertThat(result.remainingSeconds()).isEqualTo(7200L);
    }

    /**
     * 요청은 기수를 받지 않는다. 출근한 기수로 점유하는 것이 자연스러우므로 점유자
     * 멤버십은 열린 재실 구간에서 도출한다 — 다기수 담당자도 별도 선택이 없다.
     */
    @Test
    @DisplayName("점유자 멤버십은 재실 구간에서 가져온다.")
    void test2() {
        givenLockedRoom();
        givenSavedOccupancy();

        roomOccupancyService.start(SPACE_ID, USER_ID);

        RoomOccupancy saved = capturedOccupancy();
        assertThat(saved.getOccupierMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(saved.getOccupierUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSpaceId()).isEqualTo(SPACE_ID);
    }

    @Test
    @DisplayName("점유에 성공하면 점유자가 참여자로 함께 등록된다.")
    void test3() {
        givenLockedRoom();
        givenSavedOccupancy();

        roomOccupancyService.start(SPACE_ID, USER_ID);

        ArgumentCaptor<OccupancyParticipant> captor =
                ArgumentCaptor.forClass(OccupancyParticipant.class);
        verify(participantRepository).save(captor.capture());

        OccupancyParticipant participant = captor.getValue();
        assertThat(participant.getOccupancyId()).isEqualTo(OCCUPANCY_ID);
        assertThat(participant.getCohortMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(participant.getUserId()).isEqualTo(USER_ID);
        assertThat(participant.getJoinedAt()).isEqualTo(now());
        assertThat(participant.isActive()).isTrue();
    }

    @Test
    @DisplayName("없는 공간은 점유할 수 없다.")
    void test4() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.SPACE_NOT_FOUND,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("회의실이 아닌 공간은 점유할 수 없다.")
    void test5() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(false, true)));

        assertBusinessError(
                OccupancyErrorCode.NOT_MEETING_ROOM,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("비활성 공간은 점유할 수 없다.")
    void test6() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, false)));

        assertBusinessError(
                OccupancyErrorCode.SPACE_INACTIVE,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("재실이 아니면 점유할 수 없다.")
    void test7() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, true)));
        given(attendancePresenceQueryService.findOpenPresence(USER_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.NOT_PRESENT,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    /**
     * 활성 조건을 락 쿼리에 넣지 않는 이유를 고정한다. 조건에 넣으면 이 상황이
     * "행 없음"으로 빠져 400이어야 할 응답이 404가 된다.
     */
    @Test
    @DisplayName("락을 잡은 뒤 비활성화된 공간을 찾아낸다.")
    void test8() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, true)));
        givenPresent();
        given(spaceReader.lock(SPACE_ID)).willReturn(Optional.of(room(true, false)));

        assertBusinessError(
                OccupancyErrorCode.SPACE_INACTIVE,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("락 시점에 공간이 사라지면 없는 공간으로 처리한다.")
    void test9() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, true)));
        givenPresent();
        given(spaceReader.lock(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.SPACE_NOT_FOUND,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("이미 사용 중인 회의실은 점유할 수 없다.")
    void test10() {
        givenLockedRoom();
        given(occupancyRepository.existsActiveBySpaceId(SPACE_ID, now())).willReturn(true);

        assertBusinessError(
                OccupancyErrorCode.ROOM_ALREADY_OCCUPIED,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    @Test
    @DisplayName("이미 다른 회의실을 점유 중이면 점유할 수 없다.")
    void test11() {
        givenLockedRoom();
        given(occupancyRepository.existsActiveBySpaceId(SPACE_ID, now())).willReturn(false);
        given(occupancyRepository.existsActiveByUserId(USER_ID, now())).willReturn(true);

        assertBusinessError(
                OccupancyErrorCode.ALREADY_OCCUPYING,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );
    }

    /**
     * 재실 조회 실패는 클라이언트가 분기할 외부 계약이 없는 기술 실패다. BusinessException
     * 으로 감싸면 스택 트레이스가 사라지므로 그대로 전파해 GlobalExceptionHandler 가 받는다.
     */
    @Test
    @DisplayName("재실 조회 자체가 실패하면 감싸지 않고 그대로 전파한다.")
    void test12() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, true)));
        given(attendancePresenceQueryService.findOpenPresence(USER_ID))
                .willThrow(new IllegalStateException("출결 모듈 조회 실패"));

        assertThatThrownBy(() -> roomOccupancyService.start(SPACE_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BusinessException.class)
                .hasMessage("출결 모듈 조회 실패");
    }

    @Test
    @DisplayName("검증에 걸리면 점유도 참여자도 저장하지 않는다.")
    void test13() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, false)));

        assertBusinessError(
                OccupancyErrorCode.SPACE_INACTIVE,
                () -> roomOccupancyService.start(SPACE_ID, USER_ID)
        );

        verify(occupancyRepository, never()).save(any(RoomOccupancy.class));
        verify(participantRepository, never()).save(any(OccupancyParticipant.class));
    }

    /**
     * 출결 모듈 호출을 락 안에 넣으면 그 시간만큼 같은 회의실의 다른 요청이 전부 대기한다.
     * 트랜잭션에 외부 호출을 묶지 않는다는 규칙을 순서로 고정한다.
     */
    @Test
    @DisplayName("재실 조회는 공간 락을 잡기 전에 끝낸다.")
    void test14() {
        givenLockedRoom();
        givenSavedOccupancy();

        roomOccupancyService.start(SPACE_ID, USER_ID);

        InOrder order = inOrder(spaceReader, attendancePresenceQueryService);
        order.verify(spaceReader).find(SPACE_ID);
        order.verify(attendancePresenceQueryService).findOpenPresence(USER_ID);
        order.verify(spaceReader).lock(SPACE_ID);
    }

    /**
     * 유니크 인덱스는 status 만 보고 expires_at 은 보지 않는다. 만료됐지만 아직 ACTIVE 인
     * 행을 선검사보다 먼저 치우지 않으면 "목록에는 사용 가능인데 점유하면 409"가 남는다.
     */
    @Test
    @DisplayName("만료된 행을 정리한 뒤에 활성 점유를 확인한다.")
    void test15() {
        givenLockedRoom();
        givenSavedOccupancy();

        roomOccupancyService.start(SPACE_ID, USER_ID);

        InOrder order = inOrder(occupancyRepository, participantRepository);
        order.verify(occupancyRepository).expireStaleBySpaceId(SPACE_ID, now());
        order.verify(occupancyRepository).expireStaleByUserId(USER_ID, now());
        order.verify(occupancyRepository).existsActiveBySpaceId(SPACE_ID, now());
        order.verify(occupancyRepository).existsActiveByUserId(USER_ID, now());
        order.verify(occupancyRepository).save(any(RoomOccupancy.class));
        order.verify(participantRepository).save(any(OccupancyParticipant.class));
    }

    /**
     * 만료 정리가 점유 행만 EXPIRED로 바꾸고 멈추면 그 참여자들이 열린 채 남는다.
     * {@code uq_occupancy_participants_one_active}가 계정 기준이라 그 계정은 영구히
     * 다른 회의에 참여할 수 없고, 점유 시작이 스스로를 참여자로 등록하므로(MR-27)
     * 새 점유도 409로 막힌다.
     */
    @Test
    @DisplayName("만료 정리된 점유의 참여자를 함께 마감한다.")
    void test16() {
        givenLockedRoom();
        givenSavedOccupancy();
        OffsetDateTime endedAt = now().minusMinutes(1);
        given(occupancyRepository.expireStaleBySpaceId(SPACE_ID, now()))
                .willReturn(List.of(new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt)));
        given(occupancyRepository.expireStaleByUserId(USER_ID, now()))
                .willReturn(List.of(new RoomOccupancyRepository.ExpiredOccupancy(8L, OTHER_SPACE_ID, endedAt)));

        roomOccupancyService.start(SPACE_ID, USER_ID);

        verify(participantRepository).closeAllActiveByOccupancyId(7L, endedAt);
        verify(participantRepository).closeAllActiveByOccupancyId(8L, endedAt);
    }

    /**
     * 마감 시각은 정리를 수행한 시각이 아니라 점유의 종료 시각이다. 지금 시각을 찍으면
     * 참여 시간이 실제보다 길게 집계되고, 점유 행의 {@code ended_at}과도 어긋난다.
     */
    @Test
    @DisplayName("참여자 마감 시각은 정리 시각이 아니라 점유의 종료 시각이다.")
    void test17() {
        givenLockedRoom();
        givenSavedOccupancy();
        OffsetDateTime endedAt = now().minusHours(2);
        given(occupancyRepository.expireStaleBySpaceId(SPACE_ID, now()))
                .willReturn(List.of(new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt)));

        roomOccupancyService.start(SPACE_ID, USER_ID);

        verify(participantRepository).closeAllActiveByOccupancyId(7L, endedAt);
        verify(participantRepository, never()).closeAllActiveByOccupancyId(7L, now());
    }

    /**
     * 지금 점유하려는 방의 정리는 공실이 아니다.
     *
     * <p>발행하면 대기자들이 "비었다"는 알림을 받고 와서 409를 맞는다. 공실 알림은
     * 일회성 의사표시라({@code notified_at} 소진) 헛된 알림 하나가 그 사람의 신청을
     * 태워 없앤다 — 다시 신청하기 전까지 진짜 공실을 놓친다.</p>
     */
    @Test
    @DisplayName("점유할 방의 만료 정리는 공실로 알리지 않는다.")
    void test18() {
        givenLockedRoom();
        givenSavedOccupancy();
        OffsetDateTime endedAt = now().minusMinutes(1);
        given(occupancyRepository.expireStaleBySpaceId(SPACE_ID, now()))
                .willReturn(List.of(new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt)));

        roomOccupancyService.start(SPACE_ID, USER_ID);

        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    /**
     * 반면 계정 기준 정리는 다른 방이다. 그 방은 실제로 비었고, 여기서 EXPIRED로
     * 바꿔버리면 스케줄러가 다시 찾지 못해 알림이 영영 유실된다.
     */
    @Test
    @DisplayName("계정의 다른 방이 만료 정리되면 공실로 알린다.")
    void test19() {
        givenLockedRoom();
        givenSavedOccupancy();
        OffsetDateTime endedAt = now().minusMinutes(1);
        given(occupancyRepository.expireStaleByUserId(USER_ID, now()))
                .willReturn(List.of(new RoomOccupancyRepository.ExpiredOccupancy(8L, OTHER_SPACE_ID, endedAt)));

        roomOccupancyService.start(SPACE_ID, USER_ID);

        verify(eventPublisher).publishRoomVacated(
                new RoomVacatedEvent(OTHER_SPACE_ID, 8L, endedAt));
    }

    private SpaceReader.MeetingRoom room(boolean meetingRoom, boolean active) {
        return new SpaceReader.MeetingRoom(SPACE_ID, meetingRoom, active, 8);
    }

    private void givenPresent() {
        given(attendancePresenceQueryService.findOpenPresence(USER_ID))
                .willReturn(Optional.of(new OpenPresenceView(MEMBERSHIP_ID, NOW)));
    }

    /** 락까지 통과하는 회의실. 활성 점유 선검사는 기본값(false)에 맡긴다. */
    private void givenLockedRoom() {
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room(true, true)));
        givenPresent();
        given(spaceReader.lock(SPACE_ID)).willReturn(Optional.of(room(true, true)));
    }

    /**
     * 저장된 점유에 id를 채워 돌려준다. 참여자 등록이 occupancy.getId()를 요구하므로
     * 인자를 그대로 반환하면 점유 성공 경로를 재현할 수 없다.
     */
    private void givenSavedOccupancy() {
        given(occupancyRepository.save(any(RoomOccupancy.class)))
                .willAnswer(invocation -> {
                    RoomOccupancy occupancy = invocation.getArgument(0);
                    ReflectionTestUtils.setField(occupancy, "id", OCCUPANCY_ID);
                    return occupancy;
                });
    }

    private RoomOccupancy capturedOccupancy() {
        ArgumentCaptor<RoomOccupancy> captor = ArgumentCaptor.forClass(RoomOccupancy.class);
        verify(occupancyRepository).save(captor.capture());
        return captor.getValue();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private void assertBusinessError(ErrorCode expectedErrorCode, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expectedErrorCode));
    }
}
