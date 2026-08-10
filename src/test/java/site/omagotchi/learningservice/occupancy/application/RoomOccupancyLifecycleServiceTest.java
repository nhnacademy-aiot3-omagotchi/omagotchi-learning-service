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
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 연장·반납 (MR-06, MR-12, MR-14, MR-32).
 *
 * <p>시각이 핵심이라 {@code Clock.fixed}로 "지금"을 고정하고, 점유의 만료 시각을 그
 * 기준으로 앞뒤로 옮겨가며 경계를 확인한다. 명세서 §6의 "만료 31분 전 409, 29분 전 성공
 * + 정확히 +30분"이 그대로 test4·test5다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RoomOccupancyLifecycleServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    private static final Long SPACE_ID = 1L;
    private static final Long OCCUPANCY_ID = 100L;
    private static final Long MEMBERSHIP_ID = 10L;
    private static final UUID OCCUPIER_USER_ID = UUID.randomUUID();
    private static final UUID STRANGER_USER_ID = UUID.randomUUID();

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    @Mock
    private OccupancyEventPublisher eventPublisher;

    private Clock clock;
    private RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        roomOccupancyLifecycleService = new RoomOccupancyLifecycleService(
                occupancyRepository,
                participantRepository,
                eventPublisher,
                clock
        );
    }

    // ────────────────────────────── 연장 ──────────────────────────────

    @Test
    @DisplayName("연장하면 만료가 30분 미뤄지고 갱신된 값을 돌려준다.")
    void test1() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(20));

        RoomOccupancyResult result = roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getExpiresAt()).isEqualTo(now().plusMinutes(50));
        assertThat(result.expiresAt()).isEqualTo(now().plusMinutes(50));
        assertThat(result.extensionCount()).isEqualTo(1);
        assertThat(result.remainingSeconds()).isEqualTo(50 * 60L);
    }

    /** 연장 성공 시 같은 트랜잭션에서 리셋해야 새 만료 시각 기준으로 임박 알림이 다시 나간다. */
    @Test
    @DisplayName("연장하면 임박 알림 발송 기록이 리셋된다.")
    void test2() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(20));
        ReflectionTestUtils.setField(occupancy, "reminderSentAt", now().minusMinutes(5));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("점유자가 아니면 연장할 수 없다.")
    void test3() {
        givenActiveSummary();

        assertBusinessError(
                OccupancyErrorCode.NOT_OCCUPIER,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, STRANGER_USER_ID)
        );

        verify(occupancyRepository, never()).lockById(any());
    }

    /** 명세서 §6: 만료 31분 전 연장은 409. */
    @Test
    @DisplayName("만료 31분 전에는 연장할 수 없다.")
    void test4() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(31));

        assertBusinessError(
                OccupancyErrorCode.EXTENSION_TOO_EARLY,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );

        assertThat(occupancy.getExtensionCount()).isZero();
    }

    /** 명세서 §6: 만료 29분 전 연장은 성공하고 정확히 +30분. */
    @Test
    @DisplayName("만료 29분 전에는 연장할 수 있다.")
    void test5() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(29));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getExpiresAt()).isEqualTo(now().plusMinutes(59));
    }

    @Test
    @DisplayName("만료 30분 전 정각부터 연장할 수 있다.")
    void test6() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(30));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getExtensionCount()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("두 번 연장한 뒤 세 번째 요청은 거부된다.")
    void test7() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(20));
        ReflectionTestUtils.setField(occupancy, "extensionCount",
                RoomOccupancy.MAX_EXTENSION_COUNT);

        assertBusinessError(
                OccupancyErrorCode.EXTENSION_LIMIT_EXCEEDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );

        assertThat(occupancy.getExpiresAt()).isEqualTo(now().plusMinutes(20));
    }

    /**
     * 스케줄러(#9)가 아직 EXPIRED로 바꾸지 않아 status는 ACTIVE인 창이 있다. 그때
     * 연장을 허용하면 사실상 죽은 점유가 되살아난다 — 상태와 무관하게 시각으로 거부한다.
     */
    @Test
    @DisplayName("만료 시각이 지났으면 상태가 ACTIVE여도 연장할 수 없다.")
    void test8() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(-1));

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );

        assertThat(occupancy.isActive()).isTrue();
        assertThat(occupancy.getExtensionCount()).isZero();
    }

    /**
     * 만료 판정이 연장 창 판정보다 앞에 있어야 한다. 순서가 뒤집히면 이미 끝난 점유가
     * "아직 이릅니다"라는 엉뚱한 안내를 받는다.
     */
    @Test
    @DisplayName("만료된 점유는 연장 창 밖이어도 종료 오류로 안내한다.")
    void test9() {
        givenLockedOccupancy(expiringIn(-40));

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );
    }

    @Test
    @DisplayName("활성 점유가 없으면 연장할 수 없다.")
    void test10() {
        given(occupancyRepository.findActiveSummaryBySpaceId(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );
    }

    // ────────────────────────────── 반납 ──────────────────────────────

    @Test
    @DisplayName("반납하면 RELEASED로 바뀌고 종료 시각이 기록된다.")
    void test11() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.RELEASED);
        assertThat(occupancy.getEndedAt()).isEqualTo(now());
        assertThat(occupancy.isActive()).isFalse();
    }

    /**
     * 참여자를 남겨두면 {@code uq_occupancy_participants_one_active}가 계정 기준이라
     * 그 사람들이 영구히 다른 회의에 참여할 수 없게 된다 (MR-32).
     */
    @Test
    @DisplayName("반납하면 열린 참여자 전원이 종료 시각으로 마감된다.")
    void test12() {
        givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        verify(participantRepository).closeAllActiveByOccupancyId(OCCUPANCY_ID, now());
    }

    @Test
    @DisplayName("반납하면 공실 이벤트를 발행한다.")
    void test13() {
        givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        ArgumentCaptor<RoomVacatedEvent> captor = ArgumentCaptor.forClass(RoomVacatedEvent.class);
        verify(eventPublisher).publishRoomVacated(captor.capture());

        RoomVacatedEvent event = captor.getValue();
        assertThat(event.spaceId()).isEqualTo(SPACE_ID);
        assertThat(event.occupancyId()).isEqualTo(OCCUPANCY_ID);
        assertThat(event.vacatedAt()).isEqualTo(now());
    }

    /**
     * 발행이 마감보다 앞서면 리스너가 커밋 전 상태를 보게 될 여지가 생긴다.
     * 상태 변경을 모두 끝낸 뒤 발행하는 순서를 고정한다.
     */
    @Test
    @DisplayName("이벤트는 참여자 마감을 마친 뒤에 발행한다.")
    void test14() {
        givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        InOrder order = inOrder(participantRepository, eventPublisher);
        order.verify(participantRepository).closeAllActiveByOccupancyId(eq(OCCUPANCY_ID), any());
        order.verify(eventPublisher).publishRoomVacated(any(RoomVacatedEvent.class));
    }

    @Test
    @DisplayName("점유자가 아니면 반납할 수 없다.")
    void test15() {
        givenActiveSummary();

        assertBusinessError(
                OccupancyErrorCode.NOT_OCCUPIER,
                () -> roomOccupancyLifecycleService.release(SPACE_ID, STRANGER_USER_ID)
        );

        verify(participantRepository, never()).closeAllActiveByOccupancyId(any(), any());
        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    /**
     * 락 밖 조회는 활성으로 보였지만 락을 잡고 보니 이미 끝난 경우다. 여기서 통과시키면
     * 스케줄러가 기록한 EXPIRED를 RELEASED로 덮어써 통계가 틀어진다.
     */
    @Test
    @DisplayName("락을 잡은 뒤 종료된 점유는 반납할 수 없다.")
    void test16() {
        RoomOccupancy expired = occupancy(expiringIn(60));
        ReflectionTestUtils.setField(expired, "status", OccupancyStatus.EXPIRED);
        givenActiveSummary();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(expired));

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID)
        );

        assertThat(expired.getStatus()).isEqualTo(OccupancyStatus.EXPIRED);
        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    /** 만료 시각이 지났어도 아직 ACTIVE면 반납은 허용한다 — 연장과 달리 종료가 목적이다. */
    @Test
    @DisplayName("만료 시각이 지난 활성 점유도 반납할 수 있다.")
    void test17() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(-10));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.RELEASED);
        verify(eventPublisher).publishRoomVacated(any(RoomVacatedEvent.class));
    }

    /** 요약 조회와 락 사이에 행이 사라지는 경우다. FK가 막고 있어 실제로는 드물지만 통과시키면 안 된다. */
    @Test
    @DisplayName("락 시점에 점유 행이 없으면 종료된 점유로 처리한다.")
    void test18() {
        givenActiveSummary();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID)
        );

        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenActiveSummary() {
        given(occupancyRepository.findActiveSummaryBySpaceId(SPACE_ID)).willReturn(
                Optional.of(new RoomOccupancyRepository.ActiveOccupancy(
                        OCCUPANCY_ID, MEMBERSHIP_ID, OCCUPIER_USER_ID)));
    }

    /** 락까지 통과하는 활성 점유. 만료가 {@code now} 기준 몇 분 뒤인지로 상황을 만든다. */
    private RoomOccupancy givenLockedOccupancy(OffsetDateTime expiresAt) {
        RoomOccupancy occupancy = occupancy(expiresAt);
        givenActiveSummary();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        return occupancy;
    }

    private OffsetDateTime expiringIn(int minutes) {
        return now().plusMinutes(minutes);
    }

    /**
     * 스케줄러가 없으면 아무도 찾지 않는 방이 만료된 채 방치된다 — 목록에 계속
     * "사용 중"으로 뜨고 참여자도 열린 채다.
     */
    @Test
    @DisplayName("만료 정리는 참여자 마감과 공실 알림을 함께 수행한다.")
    void test19() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        given(occupancyRepository.expireStale(now())).willReturn(List.of(
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt)));

        assertThat(roomOccupancyLifecycleService.expireAll()).isEqualTo(1);

        verify(participantRepository).closeAllActiveByOccupancyId(7L, endedAt);
        verify(eventPublisher).publishRoomVacated(new RoomVacatedEvent(SPACE_ID, 7L, endedAt));
    }

    /**
     * {@code vacatedAt}이 정리 시각이면 "언제 비었는지"가 스케줄러 주기만큼 밀린다.
     * 늦게 발견했을 뿐 실제로 비워진 것은 만료 시각이다.
     */
    @Test
    @DisplayName("공실 시각은 정리 시각이 아니라 점유의 만료 시각이다.")
    void test20() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        given(occupancyRepository.expireStale(now())).willReturn(List.of(
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt)));

        roomOccupancyLifecycleService.expireAll();

        verify(eventPublisher, never()).publishRoomVacated(
                new RoomVacatedEvent(SPACE_ID, 7L, now()));
    }

    @Test
    @DisplayName("만료된 점유가 없으면 아무것도 하지 않는다.")
    void test21() {
        given(occupancyRepository.expireStale(now())).willReturn(List.of());

        assertThat(roomOccupancyLifecycleService.expireAll()).isZero();

        verify(participantRepository, never()).closeAllActiveByOccupancyId(any(), any());
        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    /** 여러 방이 동시에 만료되면 각각 따로 알려야 한다 — 대기자는 방마다 다르다. */
    @Test
    @DisplayName("여러 점유가 만료되면 방마다 공실을 알린다.")
    void test22() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        given(occupancyRepository.expireStale(now())).willReturn(List.of(
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt),
                new RoomOccupancyRepository.ExpiredOccupancy(8L, 2L, endedAt)));

        assertThat(roomOccupancyLifecycleService.expireAll()).isEqualTo(2);

        verify(eventPublisher).publishRoomVacated(new RoomVacatedEvent(SPACE_ID, 7L, endedAt));
        verify(eventPublisher).publishRoomVacated(new RoomVacatedEvent(2L, 8L, endedAt));
    }

    private RoomOccupancy occupancy(OffsetDateTime expiresAt) {
        RoomOccupancy occupancy = RoomOccupancy.start(
                SPACE_ID, MEMBERSHIP_ID, OCCUPIER_USER_ID, now().minusHours(2), expiresAt);
        ReflectionTestUtils.setField(occupancy, "id", OCCUPANCY_ID);
        return occupancy;
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
