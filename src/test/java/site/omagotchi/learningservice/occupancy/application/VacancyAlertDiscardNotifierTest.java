package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 공간 비활성화 삭제 통보 (RM-15).
 *
 * <p>{@code VacancyAlertDispatcherTest}와 같은 구조로 고정한다 — 같은 규약(부분 실패 격리·
 * sender 없음 no-op·배치 조회 1회·수신자 못 찾음 스킵)을 공유하는데 한쪽만 테스트가 있으면,
 * 이쪽이 그 규약을 조용히 어겨도 아무도 모른다.</p>
 *
 * <p>단 한 가지가 다르다 — <b>실패한 통보는 영영 사라진다</b> (at-most-once, 명세 04 §4).
 * 발송 쪽은 실패한 신청이 대기로 남아 다음 공실에 재발송되지만, 여기는 원천 행이 이미
 * 삭제돼 재시도할 수 없다. 그래서 "실패해도 소진되지 않는다"류의 검증이 여기엔 없다 —
 * 소진시킬 상태 자체가 없다.</p>
 */
@ExtendWith(MockitoExtension.class)
class VacancyAlertDiscardNotifierTest {

    private static final Long SPACE_ID = 1L;
    private static final String SPACE_NAME = "테스트 회의실";
    private static final OffsetDateTime DISCARDED_AT =
            OffsetDateTime.parse("2026-07-24T10:00:00+09:00");

    private static final Long MEMBERSHIP_A = 10L;
    private static final Long MEMBERSHIP_B = 11L;
    private static final Long MEMBERSHIP_C = 12L;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID USER_C = UUID.randomUUID();

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private SpaceNameQueryService spaceNameQueryService;

    @Mock
    private VacancyAlertSender sender;

    private VacancyAlertDiscardNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = withSender(sender);
    }

    private VacancyAlertDiscardNotifier withSender(VacancyAlertSender configured) {
        return new VacancyAlertDiscardNotifier(
                cohortMembershipQueryService,
                spaceNameQueryService,
                configured == null ? List.of() : List.of(configured)
        );
    }

    @Test
    @DisplayName("(구)신청자 전원에게 통보하고 건수를 돌려준다.")
    void notifiesEveryFormerApplicant() {
        givenThreeRecipients();

        int sent = notifier.notifyDiscarded(
                SPACE_ID, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISCARDED_AT);

        assertThat(sent).isEqualTo(3);

        ArgumentCaptor<VacancyAlertSender.DiscardNotice> captor =
                ArgumentCaptor.forClass(VacancyAlertSender.DiscardNotice.class);
        verify(sender, times(3)).sendDiscardNotice(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(VacancyAlertSender.DiscardNotice::recipientUserId)
                .containsExactly(USER_A, USER_B, USER_C);
        assertThat(captor.getAllValues()).allSatisfy(notice -> {
            assertThat(notice.spaceId()).isEqualTo(SPACE_ID);
            assertThat(notice.spaceName()).isEqualTo(SPACE_NAME);
            assertThat(notice.discardedAt()).isEqualTo(DISCARDED_AT);
        });
    }

    /**
     * 한 사람의 발송 실패가 나머지를 막으면 뒤쪽 사람들이 <b>앞사람 때문에</b> 통보를
     * 받지 못한다. 실패한 그 한 건은 재시도 원천이 없어 그대로 사라진다 — 그것이
     * at-most-once의 대가이며, 기록이 유일한 대응이다.
     */
    @Test
    @DisplayName("한 명의 발송이 실패해도 나머지에게는 통보한다.")
    void oneFailureDoesNotBlockOthers() {
        givenThreeRecipients();
        // argThat 스텁은 조건이 맞지 않는 뒤 호출에도 걸릴 수 있어, 대상 판정을 답변 안에서 한다.
        willAnswer(invocation -> {
            VacancyAlertSender.DiscardNotice notice = invocation.getArgument(0);
            if (USER_B.equals(notice.recipientUserId())) {
                throw new IllegalStateException("발송 실패");
            }
            return null;
        }).given(sender).sendDiscardNotice(any());

        int sent = notifier.notifyDiscarded(
                SPACE_ID, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISCARDED_AT);

        assertThat(sent).isEqualTo(2);
        verify(sender).sendDiscardNotice(argThat(n -> USER_C.equals(n.recipientUserId())));
    }

    /**
     * {@code findUserIds}가 계약으로 못박은 배치 성질을 지킨다. 건별로 되물으면 신청자가
     * 늘어난 만큼 기수 조회도 늘어나고, 기수 모듈이 분리되면 그대로 N+1 원격 호출이 된다.
     */
    @Test
    @DisplayName("수신자 조회는 신청자 수와 무관하게 1회다.")
    void resolvesRecipientsInASingleBatch() {
        givenThreeRecipients();

        notifier.notifyDiscarded(
                SPACE_ID, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISCARDED_AT);

        verify(cohortMembershipQueryService, times(1)).findUserIds(anyCollection());
        verify(spaceNameQueryService, times(1)).findName(SPACE_ID);
    }

    /** 수신자를 못 찾는 것은 조회 실패다. 발송을 시도하면 수신자 없는 통보가 된다. */
    @Test
    @DisplayName("수신자를 찾지 못한 신청자는 건너뛰고 나머지에게는 통보한다.")
    void skipsRecipientThatCannotBeResolved() {
        given(cohortMembershipQueryService.findUserIds(anyCollection()))
                .willReturn(Map.of(MEMBERSHIP_A, USER_A));
        givenSpaceName();

        int sent = notifier.notifyDiscarded(
                SPACE_ID, List.of(MEMBERSHIP_A, MEMBERSHIP_B), DISCARDED_AT);

        assertThat(sent).isEqualTo(1);
        verify(sender, times(1)).sendDiscardNotice(any());
    }

    /**
     * 이름 조회 실패가 통보 자체를 막으면 안 된다 — 어느 방인지 몰라도 신청이 사라졌다는
     * 사실은 알아야 한다.
     */
    @Test
    @DisplayName("공간 이름을 찾지 못해도 식별자로 대체해 통보한다.")
    void fallsBackToSpaceIdWhenNameIsMissing() {
        given(cohortMembershipQueryService.findUserIds(anyCollection()))
                .willReturn(Map.of(MEMBERSHIP_A, USER_A));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.empty());

        notifier.notifyDiscarded(SPACE_ID, List.of(MEMBERSHIP_A), DISCARDED_AT);

        verify(sender).sendDiscardNotice(
                argThat(notice -> ("공간 " + SPACE_ID).equals(notice.spaceName())));
    }

    @Test
    @DisplayName("대상이 비어 있으면 수신자를 조회하지 않는다.")
    void doesNothingWithoutRecipients() {
        assertThat(notifier.notifyDiscarded(SPACE_ID, List.of(), DISCARDED_AT)).isZero();

        verify(cohortMembershipQueryService, never()).findUserIds(anyCollection());
    }

    /**
     * 발송 수단이 <b>정확히 하나</b>가 아니면 기동을 멈춘다. 0개는 보낼 방법이 없는 것이고,
     * 둘 이상은 어느 발송의 성공을 완료로 볼지 정할 수 없다.
     *
     * <p>주입을 {@code Optional}이나 단건으로 되돌리면 후자의 방어가 사라진다 —
     * {@code @Primary}가 붙은 후보 하나만 조용히 선택되고 나머지는 무시된다.
     * 생성자가 {@code List}를 받는 이유다.</p>
     */
    @Test
    @DisplayName("sender가 없으면 생성 시점에 실패한다.")
    void failsToCreateWithoutSender() {
        assertThatThrownBy(() -> withSender(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정확히 하나여야 합니다");
    }

    @Test
    @DisplayName("sender가 둘 이상이면 생성 시점에 실패한다.")
    void failsToCreateWithMultipleSenders() {
        VacancyAlertSender another = mock(VacancyAlertSender.class);

        assertThatThrownBy(() -> new VacancyAlertDiscardNotifier(
                cohortMembershipQueryService,
                spaceNameQueryService,
                List.of(sender, another)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정확히 하나여야 합니다");
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenThreeRecipients() {
        given(cohortMembershipQueryService.findUserIds(anyCollection())).willReturn(Map.of(
                MEMBERSHIP_A, USER_A,
                MEMBERSHIP_B, USER_B,
                MEMBERSHIP_C, USER_C));
        givenSpaceName();
    }

    private void givenSpaceName() {
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));
    }
}
