package site.omagotchi.learningservice.environment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.environment.application.port.ActionNotificationSender;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 조치 알림의 <b>수신자 범위</b>를 고정한다.
 *
 * <p>매니저는 자기 기수의 일만 본다. 센서 이벤트는 기기까지만 알고 기수는 모르므로
 * {@code deviceEui → 공간 → 기수}로 되짚는데, 세 칸 어디서든 끊길 수 있다 — 그때
 * <b>전원으로 넓히지 않는 것</b>이 이 Class의 핵심 계약이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ActionNotifierTest {

    private static final String DEVICE_EUI = "24E124445D010203";
    private static final Long SPACE_ID = 11L;
    private static final Long COHORT_ID = 3L;
    private static final UUID MANAGER_A = UUID.randomUUID();
    private static final UUID MANAGER_B = UUID.randomUUID();

    @Mock
    private CohortMembershipQueryService membershipQueryService;

    @Mock
    private SensorDeviceService sensorDeviceService;

    @Mock
    private SpaceCohortQueryService spaceCohortQueryService;

    @Mock
    private ActionNotificationSender sender;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);

    // ────────────────────────────── 수신자 범위 ──────────────────────────────

    @Test
    @DisplayName("감지가 일어난 공간의 담당 기수 매니저에게만 보낸다.")
    void notifiesOnlyManagersOfOwningCohort() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID))
                .willReturn(List.of(MANAGER_A, MANAGER_B));
        given(sender.send(any(), any())).willReturn(true);

        Instant notifiedAt = notifyAction();

        assertThat(notifiedAt).isEqualTo(clock.instant());
        verify(sender).send(argThat(notice -> MANAGER_A.equals(notice.recipientUserId())), any());
        verify(sender).send(argThat(notice -> MANAGER_B.equals(notice.recipientUserId())), any());
        verify(membershipQueryService).findActiveManagerUserIds(COHORT_ID);
    }

    /**
     * 기기가 어느 공간 것인지 모르면 담당 기수도 모른다. <b>전원에게 보내지 않는다</b> —
     * 남의 기수 실습실 이야기가 섞이고, 배정이 빠진 사실도 드러나지 않는다.
     */
    @Test
    @DisplayName("기기에 공간이 배정되지 않았으면 보내지 않는다.")
    void sendsNothingWhenDeviceHasNoSpace() {
        given(sensorDeviceService.findSpaceId(DEVICE_EUI)).willReturn(Optional.empty());

        assertThat(notifyAction()).isNull();
        verifyNoInteractions(membershipQueryService, sender);
    }

    @Test
    @DisplayName("공간에 기수가 배정되지 않았으면 보내지 않는다.")
    void sendsNothingWhenSpaceHasNoCohort() {
        given(sensorDeviceService.findSpaceId(DEVICE_EUI)).willReturn(Optional.of(SPACE_ID));
        given(spaceCohortQueryService.findCohortId(SPACE_ID)).willReturn(Optional.empty());

        assertThat(notifyAction()).isNull();
        verifyNoInteractions(membershipQueryService, sender);
    }

    /**
     * {@code SensorDetection}은 {@code type}과 {@code receivedAt}만 보장한다. EUI가 없는
     * 이벤트로는 기기 조회 자체를 시도하지 않는다.
     */
    @Test
    @DisplayName("이벤트에 deviceEui가 없으면 조회조차 하지 않는다.")
    void sendsNothingWithoutDeviceEui() {
        assertThat(notifier().notifyConfirmed(detection(null), IotAction.VENTILATE, result())).isNull();

        verifyNoInteractions(sensorDeviceService, spaceCohortQueryService, membershipQueryService, sender);
    }

    @Test
    @DisplayName("기수에 활성 매니저가 없으면 보내지 않는다.")
    void sendsNothingWhenCohortHasNoManager() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID)).willReturn(List.of());

        assertThat(notifyAction()).isNull();
        verifyNoInteractions(sender);
    }

    // ────────────────────────────── 발송 결과 ──────────────────────────────

    /**
     * 미연동 매니저는 실패가 아니다. 한 명도 실제로 받지 못했으면 완료 시각을 남기지 않아
     * "보냈다"는 기록이 사실과 어긋나지 않게 한다.
     */
    @Test
    @DisplayName("아무도 실제로 받지 못하면 완료 시각을 남기지 않는다.")
    void returnsNullWhenNobodyActuallyReceived() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID)).willReturn(List.of(MANAGER_A));
        given(sender.send(any(), any())).willReturn(false);

        assertThat(notifyAction()).isNull();
    }

    /**
     * 한 사람의 발송 실패가 나머지를 막지 않는다. 여기서 전파시키면 뒤쪽 매니저들이
     * <b>앞사람의 실패 때문에</b> 알림을 받지 못한다.
     */
    @Test
    @DisplayName("한 명의 발송 실패가 나머지를 막지 않는다.")
    void continuesAfterOneFailure() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID))
                .willReturn(List.of(MANAGER_A, MANAGER_B));
        given(sender.send(any(), any())).willAnswer(invocation -> {
            ActionNotificationSender.ActionNotice notice = invocation.getArgument(0);

            if (MANAGER_A.equals(notice.recipientUserId())) {
                throw new IllegalStateException("발송 실패");
            }
            return true;
        });

        assertThat(notifyAction()).isEqualTo(clock.instant());
    }

    /**
     * <b>검사만 하는 것으로는 부족하다.</b> 남은 예산을 넘기지 않으면 마지막 한 건이
     * 예산을 넘겨 시작해, MQ 리스너가 예산 + read 타임아웃만큼 묶인다.
     */
    @Test
    @DisplayName("남은 예산을 발송 제한 시간으로 넘긴다.")
    void passesRemainingBudgetAsSendTimeout() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID)).willReturn(List.of(MANAGER_A));
        given(sender.send(any(), any())).willReturn(true);

        notifyAction();

        ArgumentCaptor<Duration> timeout = ArgumentCaptor.forClass(Duration.class);
        verify(sender).send(any(), timeout.capture());
        assertThat(timeout.getValue())
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    /**
     * 예산이 0이면 첫 수신자 전에 이미 마감을 지난다. MQ 리스너를 붙잡는 시간의 상한이
     * 수신자 수와 무관하게 유지되는지를 고정한다.
     */
    @Test
    @DisplayName("발송 예산을 넘기면 남은 수신자를 건너뛰고 중단한다.")
    void stopsWhenBudgetExhausted() {
        givenCohortResolved();
        given(membershipQueryService.findActiveManagerUserIds(COHORT_ID))
                .willReturn(List.of(MANAGER_A, MANAGER_B));

        assertThat(notifier(Duration.ofNanos(1))
                .notifyConfirmed(detection(DEVICE_EUI), IotAction.VENTILATE, result())).isNull();
        verify(sender, never()).send(any(), any());
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenCohortResolved() {
        given(sensorDeviceService.findSpaceId(DEVICE_EUI)).willReturn(Optional.of(SPACE_ID));
        given(spaceCohortQueryService.findCohortId(SPACE_ID)).willReturn(Optional.of(COHORT_ID));
    }

    private Instant notifyAction() {
        return notifier().notifyConfirmed(detection(DEVICE_EUI), IotAction.VENTILATE, result());
    }

    private ActionNotifier notifier() {
        return notifier(Duration.ofSeconds(5));
    }

    private ActionNotifier notifier(Duration budget) {
        return new ActionNotifier(
                membershipQueryService,
                sensorDeviceService,
                spaceCohortQueryService,
                new EnvironmentProperties(null, null, budget, null),
                clock,
                sender
        );
    }

    private static SensorDetection detection(String deviceEui) {
        return new SensorDetection(
                "trace-1", SensorEventType.RULE_HIT, "301호", "천장", deviceEui,
                "pm10", 85.0, null, Operator.GT, 80.0,
                Instant.parse("2026-08-27T09:59:00Z"), Instant.parse("2026-08-27T09:59:01Z"));
    }

    private static IotActionResult result() {
        return new IotActionResult(true, Instant.parse("2026-08-27T09:59:30Z"), false, null);
    }
}
