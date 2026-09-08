package site.omagotchi.learningservice.environment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.environment.application.port.ActionCoolDownStore;
import site.omagotchi.learningservice.environment.application.port.IotActionExecutor;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.ActionOutcome;
import site.omagotchi.learningservice.environment.domain.ActionStatus;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.sensor.domain.Operator;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IotActionDispatcherTest {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-09-08T01:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-09-08T01:00:01Z");

    @Mock
    private ActionCoolDownStore coolDownStore;

    @Mock
    private IotActionExecutor executor;

    @Mock
    private ActionNotifier notifier;

    private IotActionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new IotActionDispatcher(
                coolDownStore,
                executor,
                new EnvironmentProperties(null, Duration.ofMinutes(5), null, null),
                notifier
        );
    }

    @Test
    @DisplayName("룰 히트 알림을 보낸 뒤 IoT 조치를 요청하고 두 결과를 함께 남긴다.")
    void notifiesBeforeExecutingAction() {
        SensorDetection detection = ruleHit();
        given(coolDownStore.tryAcquire(anyString(), any())).willReturn(true);
        given(notifier.notifyRuleHit(detection)).willReturn(NOTIFIED_AT);
        given(executor.execute(IotAction.VENTILATE, detection))
                .willReturn(new IotActionResult(true, CONFIRMED_AT, true, null));

        ActionOutcome outcome = dispatcher.dispatch(SensorEvent.of(detection));

        assertThat(outcome.status()).isEqualTo(ActionStatus.CONFIRMED);
        assertThat(outcome.confirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(outcome.notifiedAt()).isEqualTo(NOTIFIED_AT);

        InOrder order = inOrder(notifier, executor);
        order.verify(notifier).notifyRuleHit(detection);
        order.verify(executor).execute(IotAction.VENTILATE, detection);
    }

    @Test
    @DisplayName("텔레그램 발송 뒤 IoT 조치가 실패해도 발송 시각을 보존한다.")
    void retainsNotificationTimeWhenActionFails() {
        SensorDetection detection = ruleHit();
        given(coolDownStore.tryAcquire(anyString(), any())).willReturn(true);
        given(notifier.notifyRuleHit(detection)).willReturn(NOTIFIED_AT);
        given(executor.execute(IotAction.VENTILATE, detection))
                .willReturn(IotActionResult.failure("connection refused"));

        ActionOutcome outcome = dispatcher.dispatch(SensorEvent.of(detection));

        assertThat(outcome.status()).isEqualTo(ActionStatus.FAILED);
        assertThat(outcome.error()).isEqualTo("connection refused");
        assertThat(outcome.notifiedAt()).isEqualTo(NOTIFIED_AT);
    }

    @Test
    @DisplayName("쿨다운으로 조치를 건너뛴 룰 히트는 중복 알림도 보내지 않는다.")
    void skipsDuplicateNotificationDuringCooldown() {
        SensorDetection detection = ruleHit();
        given(coolDownStore.tryAcquire(anyString(), any())).willReturn(false);

        ActionOutcome outcome = dispatcher.dispatch(SensorEvent.of(detection));

        assertThat(outcome.status()).isEqualTo(ActionStatus.SKIPPED);
        verifyNoInteractions(notifier, executor);
    }

    private static SensorDetection ruleHit() {
        return new SensorDetection(
                "trace-1",
                SensorEventType.RULE_HIT,
                "301호",
                "천장",
                "24E124445D010203",
                "co2",
                1200.0,
                null,
                Operator.GT,
                1000.0,
                Instant.parse("2026-09-08T00:59:59Z"),
                Instant.parse("2026-09-08T01:00:00Z")
        );
    }
}
