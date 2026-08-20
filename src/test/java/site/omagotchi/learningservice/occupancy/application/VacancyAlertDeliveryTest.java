package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 공실 알림 한 건의 발송·소진 (MR-03, MR-04, MR-16). */
@ExtendWith(MockitoExtension.class)
class VacancyAlertDeliveryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    private static final Long ALERT_ID = 500L;
    private static final Long SPACE_ID = 1L;
    private static final String SPACE_NAME = "테스트 회의실";
    private static final Long MEMBERSHIP_ID = 10L;
    private static final UUID RECIPIENT_USER_ID = UUID.randomUUID();
    private static final OffsetDateTime VACATED_AT =
            OffsetDateTime.parse("2026-07-24T09:30:00+09:00");

    @Mock
    private VacancyAlertRepository alertRepository;

    @Mock
    private VacancyAlertSender sender;

    private Clock clock;
    private VacancyAlertDelivery alertDelivery;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        alertDelivery = new VacancyAlertDelivery(alertRepository, clock);
    }

    @Test
    @DisplayName("발송에 성공하면 신청을 소진 처리한다.")
    void marksNotifiedAfterSuccessfulSend() {
        VacancyAlert alert = givenWaitingAlert();

        boolean sent = alertDelivery.send(ALERT_ID, SPACE_ID, SPACE_NAME, RECIPIENT_USER_ID, VACATED_AT, sender);

        assertThat(sent).isTrue();
        assertThat(alert.isWaiting()).isFalse();
        assertThat(alert.getNotifiedAt()).isEqualTo(OffsetDateTime.now(clock));
    }

    @Test
    @DisplayName("알림 본문에 점유자 정보를 담지 않는다.")
    void noticeCarriesNoOccupantInformation() {
        givenWaitingAlert();

        alertDelivery.send(ALERT_ID, SPACE_ID, SPACE_NAME, RECIPIENT_USER_ID, VACATED_AT, sender);

        ArgumentCaptor<VacancyAlertSender.VacancyNotice> captor =
                ArgumentCaptor.forClass(VacancyAlertSender.VacancyNotice.class);
        verify(sender).sendVacancyAlert(captor.capture());

        VacancyAlertSender.VacancyNotice notice = captor.getValue();
        assertThat(notice.alertId()).isEqualTo(ALERT_ID);
        assertThat(notice.spaceId()).isEqualTo(SPACE_ID);
        assertThat(notice.spaceName()).isEqualTo(SPACE_NAME);
        assertThat(notice.recipientUserId()).isEqualTo(RECIPIENT_USER_ID);
        assertThat(notice.vacatedAt()).isEqualTo(VACATED_AT);
    }

    /**
     * 발송 실패는 대기 유지 + 로깅이다 (§3). 여기서 소진시키면 그 사람은 알림을 받지도
     * 못한 채 신청만 사라진다.
     */
    @Test
    @DisplayName("발송이 실패하면 소진하지 않고 예외를 그대로 올린다.")
    void keepsAlertWaitingWhenSendFails() {
        VacancyAlert alert = givenWaitingAlert();
        willThrow(new IllegalStateException("발송 실패"))
                .given(sender).sendVacancyAlert(any());

        assertThatThrownBy(() -> alertDelivery.send(ALERT_ID, SPACE_ID, SPACE_NAME, RECIPIENT_USER_ID, VACATED_AT, sender))
                .isInstanceOf(IllegalStateException.class);

        assertThat(alert.isWaiting()).isTrue();
    }

    @Test
    @DisplayName("후보 조회 뒤 취소·소진된 신청은 발송하지 않는다.")
    void skipsAlertConsumedAfterCandidateLookup() {
        given(alertRepository.lockWaitingById(ALERT_ID)).willReturn(Optional.empty());

        assertThat(alertDelivery.send(ALERT_ID, SPACE_ID, SPACE_NAME, RECIPIENT_USER_ID, VACATED_AT, sender)).isFalse();

        verify(sender, never()).sendVacancyAlert(any());
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private VacancyAlert givenWaitingAlert() {
        VacancyAlert alert = VacancyAlert.request(
                SPACE_ID, MEMBERSHIP_ID, OffsetDateTime.now(clock).minusHours(1));
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        given(alertRepository.lockWaitingById(ALERT_ID)).willReturn(Optional.of(alert));
        return alert;
    }
}
