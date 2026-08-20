package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository.WaitingAlert;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 공실 발생 시 대기자 전원 발송 (MR-03, MR-16, MR-18).
 *
 * <p>부분 실패가 이 Class의 핵심 계약이다. 한 사람의 발송 실패가 뒤 사람의 알림을 막으면
 * 안 되고(§5), 실패한 신청은 소진되지 않아 다음 공실에 다시 대상이 되어야 한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class VacancyAlertDispatcherTest {

    private static final Long SPACE_ID = 1L;
    private static final String SPACE_NAME = "테스트 회의실";
    private static final OffsetDateTime VACATED_AT =
            OffsetDateTime.parse("2026-07-24T10:00:00+09:00");

    private static final Long MEMBERSHIP_A = 10L;
    private static final Long MEMBERSHIP_B = 11L;
    private static final Long MEMBERSHIP_C = 12L;
    private static final Long ALERT_A = 100L;
    private static final Long ALERT_B = 101L;
    private static final Long ALERT_C = 102L;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID USER_C = UUID.randomUUID();

    @Mock
    private VacancyAlertRepository alertRepository;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private SpaceNameQueryService spaceNameQueryService;

    @Mock
    private VacancyAlertDelivery alertDelivery;

    @Mock
    private VacancyAlertSender sender;

    private VacancyAlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = withSender(sender);
    }

    private VacancyAlertDispatcher withSender(VacancyAlertSender configured) {
        return new VacancyAlertDispatcher(
                alertRepository,
                cohortMembershipQueryService,
                spaceNameQueryService,
                alertDelivery,
                Optional.ofNullable(configured)
        );
    }

    @Test
    @DisplayName("대기자 전원에게 발송하고 건수를 돌려준다.")
    void dispatchesToEveryWaitingApplicant() {
        givenThreeWaitingApplicants();
        given(alertDelivery.send(anyLong(), eq(SPACE_ID), any(), any(), eq(VACATED_AT), eq(sender)))
                .willReturn(true);

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(3);

        verify(alertDelivery).send(ALERT_A, SPACE_ID, SPACE_NAME, USER_A, VACATED_AT, sender);
        verify(alertDelivery).send(ALERT_B, SPACE_ID, SPACE_NAME, USER_B, VACATED_AT, sender);
        verify(alertDelivery).send(ALERT_C, SPACE_ID, SPACE_NAME, USER_C, VACATED_AT, sender);
    }

    /**
     * {@code findUserIds}가 계약으로 못박은 배치 성질을 지킨다. 건별로 되물으면 대기자가
     * 늘어난 만큼 기수 조회도 늘어나고, 기수 모듈이 분리되면 그대로 N+1 원격 호출이 된다.
     */
    @Test
    @DisplayName("수신자 조회는 대기자 수와 무관하게 1회다.")
    void resolvesRecipientsInASingleBatch() {
        givenThreeWaitingApplicants();
        given(alertDelivery.send(anyLong(), eq(SPACE_ID), any(), any(), eq(VACATED_AT), eq(sender)))
                .willReturn(true);

        dispatcher.dispatch(SPACE_ID, VACATED_AT);

        verify(cohortMembershipQueryService, times(1)).findUserIds(anyCollection());
    }

    /**
     * 명세 §5 "신청자 5명 중 2명 발송 실패 → 성공 3건만 소진". 여기서 예외를 전파시키면
     * 뒤쪽 대기자들이 앞사람의 실패 때문에 알림을 받지 못한다.
     */
    @Test
    @DisplayName("한 건이 실패해도 나머지 대기자에게는 발송한다.")
    void oneFailureDoesNotBlockOthers() {
        givenThreeWaitingApplicants();
        given(alertDelivery.send(ALERT_A, SPACE_ID, SPACE_NAME, USER_A, VACATED_AT, sender)).willReturn(true);
        given(alertDelivery.send(ALERT_B, SPACE_ID, SPACE_NAME, USER_B, VACATED_AT, sender))
                .willThrow(new IllegalStateException("발송 실패"));
        given(alertDelivery.send(ALERT_C, SPACE_ID, SPACE_NAME, USER_C, VACATED_AT, sender)).willReturn(true);

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(2);

        verify(alertDelivery).send(ALERT_C, SPACE_ID, SPACE_NAME, USER_C, VACATED_AT, sender);
    }

    @Test
    @DisplayName("이미 취소·소진된 후보는 발송 건수에 세지 않는다.")
    void doesNotCountAlreadyConsumedCandidates() {
        givenWaiting(new WaitingAlert(ALERT_A, MEMBERSHIP_A), new WaitingAlert(ALERT_B, MEMBERSHIP_B));
        givenRecipients(Map.of(MEMBERSHIP_A, USER_A, MEMBERSHIP_B, USER_B));
        given(alertDelivery.send(ALERT_A, SPACE_ID, SPACE_NAME, USER_A, VACATED_AT, sender)).willReturn(true);
        given(alertDelivery.send(ALERT_B, SPACE_ID, SPACE_NAME, USER_B, VACATED_AT, sender)).willReturn(false);

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(1);
    }

    /**
     * 수신자를 못 찾는 것은 이 신청의 문제가 아니라 조회 실패다. 발송을 시도하면 수신자
     * 없는 알림이 되고, 소진시키면 받지 못한 사람의 신청만 조용히 사라진다.
     */
    @Test
    @DisplayName("수신자를 찾지 못한 후보는 건너뛰고 나머지는 발송한다.")
    void skipsCandidateWithUnknownRecipient() {
        givenWaiting(new WaitingAlert(ALERT_A, MEMBERSHIP_A), new WaitingAlert(ALERT_B, MEMBERSHIP_B));
        givenRecipients(Map.of(MEMBERSHIP_A, USER_A));
        given(alertDelivery.send(ALERT_A, SPACE_ID, SPACE_NAME, USER_A, VACATED_AT, sender)).willReturn(true);

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(1);

        verify(alertDelivery, never()).send(eq(ALERT_B), anyLong(), any(), any(), any(), any());
    }

    /**
     * 이름 조회 실패가 발송 자체를 막으면 안 된다. 공간이 그 사이 삭제됐다고 대기자
     * 전원이 알림을 못 받으면, 정작 방이 비었다는 사실을 아무도 모른다.
     */
    @Test
    @DisplayName("공간 이름을 찾지 못해도 식별자로 대체해 발송한다.")
    void fallsBackToSpaceIdWhenNameIsMissing() {
        givenWaiting(new WaitingAlert(ALERT_A, MEMBERSHIP_A));
        givenRecipients(Map.of(MEMBERSHIP_A, USER_A));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.empty());
        given(alertDelivery.send(anyLong(), eq(SPACE_ID), any(), any(), eq(VACATED_AT), eq(sender)))
                .willReturn(true);

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(1);

        verify(alertDelivery).send(ALERT_A, SPACE_ID, "공간 " + SPACE_ID, USER_A, VACATED_AT, sender);
    }

    @Test
    @DisplayName("대기자가 없으면 수신자를 조회하지 않는다.")
    void doesNothingWithoutWaitingApplicants() {
        given(alertRepository.findWaitingBySpaceId(SPACE_ID)).willReturn(List.of());

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isZero();

        verify(cohortMembershipQueryService, never()).findUserIds(anyCollection());
        verify(alertDelivery, never()).send(anyLong(), anyLong(), any(), any(), any(), any());
    }

    /**
     * no-op으로 넘기면 발송 수단이 붙기 전에 신청이 전부 소진되어, 정작 알림이 가능해졌을
     * 때 대기자가 없다. {@code OccupancyReminderSender}가 없을 때와 같은 정책이다.
     *
     * <p>sender가 <b>둘 이상</b>인 경우는 여기서 검증하지 않는다. {@code Optional} 주입이라
     * Container가 기동 시점에 거부하므로 이 Method에 도달하지 못한다.</p>
     */
    @Test
    @DisplayName("sender가 없으면 후보를 조회하지도, 소진하지도 않는다.")
    void keepsApplicantsWaitingWhenNoSenderRegistered() {
        assertThat(withSender(null).dispatch(SPACE_ID, VACATED_AT)).isZero();

        verify(alertRepository, never()).findWaitingBySpaceId(anyLong());
        verify(alertDelivery, never()).send(anyLong(), anyLong(), any(), any(), any(), any());
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenThreeWaitingApplicants() {
        givenWaiting(
                new WaitingAlert(ALERT_A, MEMBERSHIP_A),
                new WaitingAlert(ALERT_B, MEMBERSHIP_B),
                new WaitingAlert(ALERT_C, MEMBERSHIP_C));
        givenRecipients(Map.of(
                MEMBERSHIP_A, USER_A,
                MEMBERSHIP_B, USER_B,
                MEMBERSHIP_C, USER_C));
    }

    private void givenWaiting(WaitingAlert... candidates) {
        given(alertRepository.findWaitingBySpaceId(SPACE_ID)).willReturn(List.of(candidates));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));
    }

    private void givenRecipients(Map<Long, UUID> userIdByMembershipId) {
        given(cohortMembershipQueryService.findUserIds(anyCollection()))
                .willReturn(userIdByMembershipId);
    }
}
