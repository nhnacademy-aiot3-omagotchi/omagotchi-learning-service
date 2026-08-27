package site.omagotchi.learningservice.team.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender.DisbandNotice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 팀 해체 통보 (GR-19).
 *
 * <p>{@code VacancyAlertDiscardNotifierTest}와 같은 구조로 고정한다 — 같은 규약(부분 실패
 * 격리·sender 없음 no-op·배치 조회 1회·수신자 못 찾음 스킵)을 공유하는데 한쪽만 테스트가
 * 있으면, 이쪽이 그 규약을 조용히 어겨도 아무도 모른다.</p>
 *
 * <p><b>실패한 통보는 영영 사라진다</b> (at-most-once, 명세 06 §5). 원천 행이 이미 삭제돼
 * 재시도할 수 없으므로 "실패해도 상태가 남는다"류의 검증이 여기엔 없다 — 남길 상태가 없다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TeamDisbandNotifierTest {

    private static final Long TEAM_ID = 7L;
    private static final String TEAM_NAME = "테스트팀";
    private static final OffsetDateTime DISBANDED_AT =
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
    private TeamNotificationSender sender;

    private TeamDisbandNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = withSender(sender);
    }

    private TeamDisbandNotifier withSender(TeamNotificationSender configured) {
        return new TeamDisbandNotifier(
                cohortMembershipQueryService,
                configured == null ? List.of() : List.of(configured)
        );
    }

    @Test
    @DisplayName("(구)팀원 전원에게 통보하고 건수를 돌려준다.")
    void notifiesEveryFormerMember() {
        givenThreeRecipients();

        int sent = notifier.notifyDisbanded(
                TEAM_ID, TEAM_NAME, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISBANDED_AT);

        assertThat(sent).isEqualTo(3);

        ArgumentCaptor<DisbandNotice> captor = ArgumentCaptor.forClass(DisbandNotice.class);
        verify(sender, times(3)).sendDisbandNotice(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DisbandNotice::recipientUserId)
                .containsExactly(USER_A, USER_B, USER_C);
        assertThat(captor.getAllValues()).allSatisfy(notice -> {
            assertThat(notice.teamId()).isEqualTo(TEAM_ID);
            assertThat(notice.teamName()).isEqualTo(TEAM_NAME);
            assertThat(notice.disbandedAt()).isEqualTo(DISBANDED_AT);
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
            DisbandNotice notice = invocation.getArgument(0);
            if (USER_B.equals(notice.recipientUserId())) {
                throw new IllegalStateException("발송 실패");
            }
            return null;
        }).given(sender).sendDisbandNotice(any());

        int sent = notifier.notifyDisbanded(
                TEAM_ID, TEAM_NAME, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISBANDED_AT);

        assertThat(sent).isEqualTo(2);
    }

    /**
     * {@code findUserIds}가 계약으로 못박은 배치 성질을 지킨다. 건별로 되물으면 팀원이
     * 늘어난 만큼 기수 조회도 늘어나고, 기수 모듈이 분리되면 그대로 N+1 원격 호출이 된다.
     */
    @Test
    @DisplayName("수신자 조회는 팀원 수와 무관하게 1회다.")
    void resolvesRecipientsInASingleBatch() {
        givenThreeRecipients();

        notifier.notifyDisbanded(
                TEAM_ID, TEAM_NAME, List.of(MEMBERSHIP_A, MEMBERSHIP_B, MEMBERSHIP_C), DISBANDED_AT);

        verify(cohortMembershipQueryService, times(1)).findUserIds(anyCollection());
    }

    /** 수신자를 못 찾는 것은 조회 실패다. 발송을 시도하면 수신자 없는 통보가 된다. */
    @Test
    @DisplayName("수신자를 찾지 못한 팀원은 건너뛰고 나머지에게는 통보한다.")
    void skipsRecipientThatCannotBeResolved() {
        given(cohortMembershipQueryService.findUserIds(anyCollection()))
                .willReturn(Map.of(MEMBERSHIP_A, USER_A));

        int sent = notifier.notifyDisbanded(
                TEAM_ID, TEAM_NAME, List.of(MEMBERSHIP_A, MEMBERSHIP_B), DISBANDED_AT);

        assertThat(sent).isEqualTo(1);
        verify(sender, times(1)).sendDisbandNotice(any());
    }

    /**
     * 발송 수단이 없으면 통보가 그대로 사라지는 것이 at-most-once 계약이다. 조회조차 하지
     * 않는 것은 아무에게도 보내지 않을 일에 기수 조회를 쓰지 않기 위해서다.
     */
    @Test
    @DisplayName("sender가 없으면 수신자를 조회하지 않고 끝낸다.")
    void doesNothingWithoutSender() {
        assertThat(withSender(null).notifyDisbanded(
                TEAM_ID, TEAM_NAME, List.of(MEMBERSHIP_A), DISBANDED_AT)).isZero();

        verify(cohortMembershipQueryService, never()).findUserIds(anyCollection());
    }

    @Test
    @DisplayName("대상이 비어 있으면 수신자를 조회하지 않는다.")
    void doesNothingWithoutRecipients() {
        assertThat(notifier.notifyDisbanded(TEAM_ID, TEAM_NAME, List.of(), DISBANDED_AT)).isZero();

        verify(cohortMembershipQueryService, never()).findUserIds(anyCollection());
    }

    /**
     * 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이므로, 통보가 나가는 순간이 아니라
     * 기동 시점에 멈춘다.
     */
    @Test
    @DisplayName("sender가 둘 이상이면 생성 시점에 실패한다.")
    void failsToCreateWithMultipleSenders() {
        TeamNotificationSender another = mock(TeamNotificationSender.class);

        assertThatThrownBy(() -> new TeamDisbandNotifier(
                cohortMembershipQueryService, List.of(sender, another)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void givenThreeRecipients() {
        given(cohortMembershipQueryService.findUserIds(anyCollection()))
                .willReturn(Map.of(MEMBERSHIP_A, USER_A, MEMBERSHIP_B, USER_B, MEMBERSHIP_C, USER_C));
    }
}
