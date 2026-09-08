package site.omagotchi.learningservice.environment.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.environment.application.port.ActionNotificationSender.ActionNotice;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramActionNotificationSenderTest {

    @Mock
    private TelegramNotificationService notificationService;

    @Test
    void sendsRuleHitMessageInsteadOfActionCompletionMessage() {
        UUID recipientUserId = UUID.randomUUID();
        Duration timeout = Duration.ofSeconds(3);
        ActionNotice notice = new ActionNotice(
                recipientUserId,
                "301호",
                "co2",
                1200.0,
                Operator.GT,
                1000.0,
                Instant.parse("2026-09-08T01:00:00Z")
        );
        given(notificationService.send(eq(recipientUserId), anyString(), eq(timeout))).willReturn(true);

        TelegramActionNotificationSender sender =
                new TelegramActionNotificationSender(notificationService);

        assertThat(sender.send(notice, timeout)).isTrue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notificationService).send(eq(recipientUserId), text.capture(), eq(timeout));
        assertThat(text.getValue())
                .contains("[임계 룰 감지]")
                .contains("위치: 301호")
                .contains("측정: co2 1200.0 (기준 1000.0 초과)")
                .contains("확인이 필요합니다.")
                .doesNotContain("자동 조치 완료")
                .doesNotContain("조치:");
    }
}
