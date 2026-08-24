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
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
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

    @Mock
    private StaleVacancyAlertDiscarder staleAlertDiscarder;

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
                staleAlertDiscarder,
                configured == null ? List.of() : List.of(configured)
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

    /**
     * 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이라 기동을 멈춘다.
     *
     * <p>주입을 {@code Optional}로 되돌리면 이 방어가 사라진다 — {@code @Primary}가 붙은
     * 후보 하나만 조용히 선택되고 나머지는 무시된다. 그래서 생성자가 {@code List}를 받는다.</p>
     */
    @Test
    @DisplayName("sender가 둘 이상이면 생성 시점에 실패한다.")
    void failsToCreateWithMultipleSenders() {
        // Port에 Method가 둘이라 람다로 못 만든다. 개수만 늘리면 되므로 Mock으로 충분하다.
        VacancyAlertSender another = org.mockito.Mockito.mock(VacancyAlertSender.class);

        assertThatThrownBy(() -> new VacancyAlertDispatcher(
                alertRepository,
                cohortMembershipQueryService,
                spaceNameQueryService,
                alertDelivery,
                staleAlertDiscarder,
                List.of(sender, another)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하나만 등록할 수 있습니다");
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

    private void givenTwoWaitingAlerts() {
        givenWaiting(new WaitingAlert(ALERT_A, MEMBERSHIP_A), new WaitingAlert(ALERT_B, MEMBERSHIP_B));
        givenRecipients(Map.of(MEMBERSHIP_A, USER_A, MEMBERSHIP_B, USER_B));
        given(alertDelivery.send(anyLong(), eq(SPACE_ID), any(), any(), eq(VACATED_AT), eq(sender)))
                .willReturn(true);
    }

    /**
     * 정리 훅이 아예 실행되지 않으면 지킬순서 자체가 없고
     * 남은 신청은 서비스를 떠난 사람에게 그대로 발송된다
     */
    @Test
    @DisplayName("소속이 끝난 신청자에게는 발송하지 않는다.")
    void doesNotSendToApplicantWhoseMembershipEnded() {
        givenTwoWaitingAlerts();
        given(cohortMembershipQueryService.findInactiveMembershipIds(anyCollection()))
                .willReturn(Set.of(MEMBERSHIP_A));

        dispatcher.dispatch(SPACE_ID, VACATED_AT);

        verify(alertDelivery, never()).send(
                eq(ALERT_A), anyLong(), anyString(), any(), any(), any());
        verify(alertDelivery).send(
                eq(ALERT_B), anyLong(), anyString(), eq(USER_B), any(), any());
    }

    /**
     * <b>건너뛰지 않고 지우는 것이 계약이다.</b> 이 잔여를 치울 다른 주체가 없다 — 멤버십
     * 정합성 스윕은 열린 참여 행을 커서로 돌기 때문에, 점유도 참여도 없이 신청만 남은
     * 사람은 방문하지 않는다. 건너뛰기만 하면 그 행은 공실이 생길 때마다 다시 평가된다.
     */
    @Test
    @DisplayName("소속이 끝난 신청은 건너뛰지 않고 폐기한다.")
    void discardsStaleAlertsInsteadOfSkipping() {
        givenTwoWaitingAlerts();
        given(cohortMembershipQueryService.findInactiveMembershipIds(anyCollection()))
                .willReturn(Set.of(MEMBERSHIP_A));

        dispatcher.dispatch(SPACE_ID, VACATED_AT);

        verify(staleAlertDiscarder).discard(Set.of(MEMBERSHIP_A));
    }

    /**
     * 소속 판정이 실패했다고 발송을 멈추지 않는다 — 유효한 대기자까지 함께 막히는 것이
     * 잔여 하나를 잘못 보내는 것보다 나쁘고, 순서(CE-05)가 여전히 1차 방어다.
     */
    @Test
    @DisplayName("소속 판정이 실패해도 발송은 계속한다.")
    void keepsSendingWhenStalenessCheckFails() {
        givenTwoWaitingAlerts();
        willThrow(new IllegalStateException("판정 실패"))
                .given(cohortMembershipQueryService).findInactiveMembershipIds(anyCollection());

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(2);
    }

    /** 폐기에 실패해도 유효한 대기자에게는 발송한다. 지우지 못한 행은 다음 공실에 다시 걸린다. */
    @Test
    @DisplayName("폐기에 실패해도 나머지에게는 발송한다.")
    void keepsSendingWhenDiscardFails() {
        givenTwoWaitingAlerts();
        given(cohortMembershipQueryService.findInactiveMembershipIds(anyCollection()))
                .willReturn(Set.of(MEMBERSHIP_A));
        willThrow(new IllegalStateException("폐기 실패"))
                .given(staleAlertDiscarder).discard(anyCollection());

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isEqualTo(1);

        // 건수만으로는 "누구에게 갔는지"를 보장하지 못한다 — 필터가 뒤집혀 ALERT_A가
        // 발송되고 ALERT_B가 스킵돼도 건수는 똑같이 1이다.
        verify(alertDelivery, never()).send(
                eq(ALERT_A), anyLong(), anyString(), any(), any(), any());
        verify(alertDelivery).send(
                eq(ALERT_B), anyLong(), anyString(), eq(USER_B), any(), any());
    }

    /** 전원이 무효면 발송도 수신자 조회도 없다 — 아무에게도 안 보낼 일에 기수를 되묻지 않는다. */
    @Test
    @DisplayName("대기자 전원의 소속이 끝났으면 수신자를 조회하지 않는다.")
    void skipsRecipientLookupWhenEveryApplicantIsStale() {
        // 공유 헬퍼를 쓰지 않는다 — 전원이 무효면 이름·수신자·발송 Stub이 전부 미사용이 된다.
        given(alertRepository.findWaitingBySpaceId(SPACE_ID)).willReturn(List.of(
                new WaitingAlert(ALERT_A, MEMBERSHIP_A), new WaitingAlert(ALERT_B, MEMBERSHIP_B)));
        given(cohortMembershipQueryService.findInactiveMembershipIds(anyCollection()))
                .willReturn(Set.of(MEMBERSHIP_A, MEMBERSHIP_B));

        assertThat(dispatcher.dispatch(SPACE_ID, VACATED_AT)).isZero();

        verify(cohortMembershipQueryService, never()).findUserIds(anyCollection());
        verify(staleAlertDiscarder).discard(Set.of(MEMBERSHIP_A, MEMBERSHIP_B));
    }
}
