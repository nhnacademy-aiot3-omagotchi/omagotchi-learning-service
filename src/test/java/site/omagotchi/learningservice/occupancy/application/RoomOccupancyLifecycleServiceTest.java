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
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
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

    @Mock
    private OccupancyExpiration occupancyExpiration;

    @Mock
    private OccupancyExpiryReminder occupancyExpiryReminder;

    @Mock
    private OccupancyReminderSender reminderSender;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private VacancyAlertRepository alertRepository;

    private Clock clock;
    private RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        roomOccupancyLifecycleService = new RoomOccupancyLifecycleService(
                occupancyRepository,
                participantRepository,
                eventPublisher,
                occupancyExpiration,
                occupancyExpiryReminder,
                List.of(reminderSender),
                cohortAccessService,
                cohortMembershipQueryService,
                alertRepository,
                clock
        );
    }

    // ────────────────────────────── 연장 ──────────────────────────────

    @Test
    @DisplayName("연장하면 만료가 30분 미뤄지고 갱신된 값을 돌려준다.")
    void extendPostponesExpiryByThirtyMinutes() {
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
    void extendResetsReminderSentAt() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(20));
        ReflectionTestUtils.setField(occupancy, "reminderSentAt", now().minusMinutes(5));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("점유자가 아니면 연장할 수 없다.")
    void cannotExtendWhenNotOccupier() {
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
    void cannotExtendThirtyOneMinutesBeforeExpiry() {
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
    void canExtendTwentyNineMinutesBeforeExpiry() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(29));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getExpiresAt()).isEqualTo(now().plusMinutes(59));
    }

    @Test
    @DisplayName("만료 30분 전 정각부터 연장할 수 있다.")
    void canExtendExactlyThirtyMinutesBeforeExpiry() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(30));

        roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getExtensionCount()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("두 번 연장한 뒤 세 번째 요청은 거부된다.")
    void rejectsThirdExtensionAfterTwoSucceed() {
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
    void cannotExtendAfterExpiryEvenWhileStillActive() {
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
    void expiredOccupancyReturnsEndedErrorRegardlessOfWindow() {
        givenLockedOccupancy(expiringIn(-40));

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );
    }

    @Test
    @DisplayName("활성 점유가 없으면 연장할 수 없다.")
    void cannotExtendWithoutActiveOccupancy() {
        given(occupancyRepository.findActiveSummaryBySpaceId(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> roomOccupancyLifecycleService.extend(SPACE_ID, OCCUPIER_USER_ID)
        );
    }

    // ────────────────────────────── 반납 ──────────────────────────────

    @Test
    @DisplayName("반납하면 RELEASED로 바뀌고 종료 시각이 기록된다.")
    void releaseSetsStatusReleasedAndRecordsEndedAt() {
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
    void releaseClosesAllOpenParticipantsAtEndTime() {
        givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        verify(participantRepository).closeAllActiveByOccupancyId(OCCUPANCY_ID, now());
    }

    @Test
    @DisplayName("반납하면 공실 이벤트를 발행한다.")
    void releasePublishesVacatedEvent() {
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
    void publishesEventAfterClosingParticipants() {
        givenLockedOccupancy(expiringIn(60));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        InOrder order = inOrder(participantRepository, eventPublisher);
        order.verify(participantRepository).closeAllActiveByOccupancyId(eq(OCCUPANCY_ID), any());
        order.verify(eventPublisher).publishRoomVacated(any(RoomVacatedEvent.class));
    }

    @Test
    @DisplayName("점유자가 아니면 반납할 수 없다.")
    void cannotReleaseWhenNotOccupier() {
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
    void cannotReleaseOccupancyEndedAfterLock() {
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
    void canReleaseActiveOccupancyPastExpiry() {
        RoomOccupancy occupancy = givenLockedOccupancy(expiringIn(-10));

        roomOccupancyLifecycleService.release(SPACE_ID, OCCUPIER_USER_ID);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.RELEASED);
        verify(eventPublisher).publishRoomVacated(any(RoomVacatedEvent.class));
    }

    /** 요약 조회와 락 사이에 행이 사라지는 경우다. FK가 막고 있어 실제로는 드물지만 통과시키면 안 된다. */
    @Test
    @DisplayName("락 시점에 점유 행이 없으면 종료된 점유로 처리한다.")
    void treatsMissingRowAtLockAsEnded() {
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

    /** 후보를 하나씩 건별 트랜잭션에 넘긴다 — 한 건의 실패가 나머지를 막지 않기 위해서다. */
    @Test
    @DisplayName("만료 후보를 건별로 종료 처리에 넘긴다.")
    void delegatesEachCandidateToExpirationIndividually() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        RoomOccupancyRepository.ExpiredOccupancy candidate =
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt);
        given(occupancyRepository.findStale(now())).willReturn(List.of(candidate));
        given(occupancyExpiration.expire(candidate, now())).willReturn(true);

        assertThat(roomOccupancyLifecycleService.expireAll()).isEqualTo(1);

        verify(occupancyExpiration).expire(candidate, now());
    }

    /**
     * 조회 결과가 곧 종료 건수는 아니다. 조회와 전이 사이에 연장·반납이 일어나거나 다른
     * 인스턴스가 먼저 처리하면 그 건은 건너뛰고, 그 판정은 전이의 조건이 한다.
     */
    @Test
    @DisplayName("전이되지 않은 후보는 종료 건수에 세지 않는다.")
    void doesNotCountUntransitionedCandidates() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        RoomOccupancyRepository.ExpiredOccupancy extended =
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt);
        RoomOccupancyRepository.ExpiredOccupancy expired =
                new RoomOccupancyRepository.ExpiredOccupancy(8L, 2L, endedAt);
        given(occupancyRepository.findStale(now())).willReturn(List.of(extended, expired));
        given(occupancyExpiration.expire(extended, now())).willReturn(false);
        given(occupancyExpiration.expire(expired, now())).willReturn(true);

        assertThat(roomOccupancyLifecycleService.expireAll()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 점유가 없으면 아무것도 하지 않는다.")
    void doesNothingWhenNoExpiredOccupancies() {
        given(occupancyRepository.findStale(now())).willReturn(List.of());

        assertThat(roomOccupancyLifecycleService.expireAll()).isZero();

        verify(occupancyExpiration, never()).expire(any(), any());
    }

    /**
     * <b>건별 격리의 핵심 검증이다</b> (명세서 03 §4). 한 건이 터져도 나머지가 이번 주기에
     * 처리돼야 한다 — 여기서 예외가 밖으로 나가면 뒤 순번 점유들이 다음 주기까지,
     * 그 주기에도 같은 건이 먼저 터지면 영원히 방치된다.
     */
    @Test
    @DisplayName("한 건이 실패해도 나머지 점유를 계속 처리한다.")
    void continuesProcessingRemainingCandidatesAfterOneFails() {
        OffsetDateTime endedAt = now().minusMinutes(5);
        RoomOccupancyRepository.ExpiredOccupancy failing =
                new RoomOccupancyRepository.ExpiredOccupancy(7L, SPACE_ID, endedAt);
        RoomOccupancyRepository.ExpiredOccupancy healthy =
                new RoomOccupancyRepository.ExpiredOccupancy(8L, 2L, endedAt);
        given(occupancyRepository.findStale(now())).willReturn(List.of(failing, healthy));
        given(occupancyExpiration.expire(failing, now()))
                .willThrow(new IllegalStateException("전이 실패"));
        given(occupancyExpiration.expire(healthy, now())).willReturn(true);

        assertThat(roomOccupancyLifecycleService.expireAll()).isEqualTo(1);

        verify(occupancyExpiration).expire(healthy, now());
    }

    // ────────────────────────────── 만료 임박 알림 ──────────────────────────────

    @Test
    @DisplayName("만료 10분 이내 후보를 같은 시각 기준으로 건별 알림 처리에 넘긴다.")
    void delegatesExpiringSoonCandidatesIndividually() {
        RoomOccupancyRepository.ExpiringOccupancy candidate =
                new RoomOccupancyRepository.ExpiringOccupancy(OCCUPANCY_ID, expiringIn(10));
        given(occupancyRepository.findExpiringSoon(now(), expiringIn(10)))
                .willReturn(List.of(candidate));
        given(occupancyExpiryReminder.send(candidate, reminderSender)).willReturn(true);

        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isEqualTo(1);

        verify(occupancyExpiryReminder).send(candidate, reminderSender);
    }

    @Test
    @DisplayName("한 알림이 실패해도 다음 만료 임박 점유를 계속 처리한다.")
    void continuesExpiryRemindersAfterOneFails() {
        RoomOccupancyRepository.ExpiringOccupancy failing =
                new RoomOccupancyRepository.ExpiringOccupancy(7L, expiringIn(5));
        RoomOccupancyRepository.ExpiringOccupancy healthy =
                new RoomOccupancyRepository.ExpiringOccupancy(8L, expiringIn(6));
        given(occupancyRepository.findExpiringSoon(now(), expiringIn(10)))
                .willReturn(List.of(failing, healthy));
        given(occupancyExpiryReminder.send(failing, reminderSender))
                .willThrow(new IllegalStateException("발송 실패"));
        given(occupancyExpiryReminder.send(healthy, reminderSender)).willReturn(true);

        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isEqualTo(1);

        verify(occupancyExpiryReminder).send(healthy, reminderSender);
    }

    @Test
    @DisplayName("실제 sender 구현이 없으면 후보를 조회하거나 완료 처리하지 않는다.")
    void doesNotConsumeCandidatesWithoutActualSender() {
        RoomOccupancyLifecycleService withoutSender = new RoomOccupancyLifecycleService(
                occupancyRepository,
                participantRepository,
                eventPublisher,
                occupancyExpiration,
                occupancyExpiryReminder,
                List.of(),
                cohortAccessService,
                cohortMembershipQueryService,
                alertRepository,
                clock
        );

        assertThat(withoutSender.sendExpiryReminders()).isZero();

        verify(occupancyRepository, never()).findExpiringSoon(any(), any());
        verify(occupancyExpiryReminder, never()).send(any(), any());
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
