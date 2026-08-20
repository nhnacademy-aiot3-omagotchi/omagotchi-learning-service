package site.omagotchi.learningservice.occupancy.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공실 알림 신청·취소·목록 (MR-02, MR-15, MR-17, MR-34).
 *
 * <p>이 기능의 판정은 대부분 "점유가 지금 있는가"에 걸려 있는데, 그 정의는
 * {@code occupancy}가 소유하고 만료 필터를 포함한다. 여기서 고정하는 것은 그 판정을
 * <b>다시 구현하지 않는다</b>는 계약이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class VacancyAlertServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    private static final Long SPACE_ID = 1L;
    private static final Long ALERT_ID = 500L;
    private static final Long OCCUPANCY_ID = 100L;

    private static final Long COHORT_ID = 3L;
    private static final Long OTHER_COHORT_ID = 4L;
    private static final Long MEMBERSHIP_ID = 10L;
    private static final Long OTHER_MEMBERSHIP_ID = 11L;

    private static final UUID REQUESTER_USER_ID = UUID.randomUUID();
    private static final UUID OCCUPIER_USER_ID = UUID.randomUUID();

    @Mock
    private OccupancyQueryService occupancyQueryService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private VacancyAlertRepository alertRepository;

    private Clock clock;
    private VacancyAlertService vacancyAlertService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        vacancyAlertService = new VacancyAlertService(
                occupancyQueryService,
                cohortMembershipQueryService,
                alertRepository,
                clock
        );
    }

    // ────────────────────────────── 신청 ──────────────────────────────

    @Test
    @DisplayName("사용 중인 회의실에 신청하면 활성 멤버십으로 행이 생성된다.")
    void requestCreatesRowWithActiveMembership() {
        givenOccupiedRoom();
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.save(any(VacancyAlert.class))).willAnswer(call -> call.getArgument(0));

        vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID);

        ArgumentCaptor<VacancyAlert> captor = ArgumentCaptor.forClass(VacancyAlert.class);
        verify(alertRepository).save(captor.capture());

        VacancyAlert saved = captor.getValue();
        assertThat(saved.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(saved.getCohortMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(saved.getCreatedAt()).isEqualTo(now());
        assertThat(saved.isWaiting()).isTrue();
    }

    /**
     * 회의실은 여러 기수가 공유하는 자원이다 (MR-34). 점유자 기수를 신청자와 비교하는
     * 검사를 넣으면 공유 자원이 기수별 자원으로 바뀐다.
     */
    @Test
    @DisplayName("타 기수가 점유 중인 회의실에도 신청할 수 있다.")
    void allowsRequestWhenOccupierIsFromAnotherCohort() {
        givenOccupiedRoomOwnedByCohort(OTHER_COHORT_ID);
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.save(any(VacancyAlert.class))).willAnswer(call -> call.getArgument(0));

        vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID);

        verify(alertRepository).save(any(VacancyAlert.class));
    }

    /**
     * 만료됐지만 아직 ACTIVE인 행도 여기서는 "빈 방"이다. 그 판정은
     * {@code findActiveBySpaceIds}가 {@code now}로 걸러 주므로 이 Service가 다시 하지
     * 않는다 — 직접 판정하면 목록에는 공실인데 신청은 성공하는 상태가 생긴다.
     */
    @Test
    @DisplayName("빈 회의실에는 신청할 수 없다.")
    void cannotRequestForAvailableRoom() {
        given(occupancyQueryService.findActiveBySpaceIds(List.of(SPACE_ID), now()))
                .willReturn(Map.of());

        assertBusinessError(
                OccupancyErrorCode.ALERT_ROOM_AVAILABLE,
                () -> vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID)
        );

        verify(alertRepository, never()).save(any(VacancyAlert.class));
    }

    @Test
    @DisplayName("본인이 점유 중인 회의실에는 신청할 수 없다.")
    void occupierCannotRequestForOwnRoom() {
        givenOccupiedRoom();

        assertBusinessError(
                OccupancyErrorCode.ALERT_OCCUPIER_CANNOT_REQUEST,
                () -> vacancyAlertService.request(SPACE_ID, null, OCCUPIER_USER_ID)
        );

        verify(alertRepository, never()).save(any(VacancyAlert.class));
    }

    /**
     * 다기수 담당자는 같은 방에 기수마다 신청할 수 있어야 한다 (§4). 서버가 하나를
     * 임의로 고르면 사용자가 의도한 기수와 다른 소속으로 신청될 수 있다.
     */
    @Test
    @DisplayName("활성 소속이 여럿인데 기수를 지정하지 않으면 지정을 요구한다.")
    void requiresCohortIdWhenMultipleMemberships() {
        givenOccupiedRoom();
        givenMemberships(
                membership(MEMBERSHIP_ID, COHORT_ID),
                membership(OTHER_MEMBERSHIP_ID, OTHER_COHORT_ID));

        assertBusinessError(
                OccupancyErrorCode.ALERT_COHORT_ID_REQUIRED,
                () -> vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID)
        );

        verify(alertRepository, never()).save(any(VacancyAlert.class));
    }

    @Test
    @DisplayName("기수를 지정하면 그 소속으로 신청한다.")
    void usesSpecifiedCohortMembership() {
        givenOccupiedRoom();
        givenMemberships(
                membership(MEMBERSHIP_ID, COHORT_ID),
                membership(OTHER_MEMBERSHIP_ID, OTHER_COHORT_ID));
        given(alertRepository.save(any(VacancyAlert.class))).willAnswer(call -> call.getArgument(0));

        vacancyAlertService.request(SPACE_ID, OTHER_COHORT_ID, REQUESTER_USER_ID);

        ArgumentCaptor<VacancyAlert> captor = ArgumentCaptor.forClass(VacancyAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getCohortMembershipId()).isEqualTo(OTHER_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("지정한 기수의 활성 소속이 없으면 신청할 수 없다.")
    void cannotRequestWithCohortTheUserDoesNotBelongTo() {
        givenOccupiedRoom();
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));

        assertBusinessError(
                OccupancyErrorCode.ALERT_COHORT_ACCESS_DENIED,
                () -> vacancyAlertService.request(SPACE_ID, OTHER_COHORT_ID, REQUESTER_USER_ID)
        );
    }

    @Test
    @DisplayName("활성 소속이 없으면 신청할 수 없다.")
    void cannotRequestWithoutActiveMembership() {
        givenOccupiedRoom();
        givenMemberships();

        assertBusinessError(
                OccupancyErrorCode.ALERT_COHORT_ACCESS_DENIED,
                () -> vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID)
        );
    }

    /**
     * 중복은 부분 유니크가 막고 Persistence가 409로 옮긴다. 여기서 select 선검사를 더하면
     * 같은 판정이 두 곳에 생기는데, 동시 요청은 어차피 인덱스에서만 잡힌다.
     */
    @Test
    @DisplayName("중복 신청은 선검사 없이 저장 시점에 걸린다.")
    void doesNotPreCheckDuplicateBeforeSaving() {
        givenOccupiedRoom();
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.save(any(VacancyAlert.class)))
                .willThrow(new BusinessException(OccupancyErrorCode.ALERT_ALREADY_REQUESTED));

        assertBusinessError(
                OccupancyErrorCode.ALERT_ALREADY_REQUESTED,
                () -> vacancyAlertService.request(SPACE_ID, null, REQUESTER_USER_ID)
        );

        verify(alertRepository, never()).findWaitingByMembershipIds(anyCollection());
    }

    // ────────────────────────────── 취소 ──────────────────────────────

    @Test
    @DisplayName("본인의 대기 중 신청을 취소하면 행이 삭제된다.")
    void cancelDeletesOwnWaitingAlert() {
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.deleteWaiting(ALERT_ID, Set.of(MEMBERSHIP_ID))).willReturn(true);

        vacancyAlertService.cancel(ALERT_ID, REQUESTER_USER_ID);

        verify(alertRepository).deleteWaiting(ALERT_ID, Set.of(MEMBERSHIP_ID));
    }

    /**
     * 없음·이미 발송·남의 것을 구분하지 않는다. 삭제 조건이 셋을 한 문장에 담고 있어
     * 0행이면 어느 쪽이든 같은 404다 — 구분하면 신청 식별자를 훑어 존재 여부가 새어 나간다.
     */
    @Test
    @DisplayName("지울 대상이 없으면 이유를 구분하지 않고 404다.")
    void cancelFailsWithSameErrorWhateverTheReason() {
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.deleteWaiting(ALERT_ID, Set.of(MEMBERSHIP_ID))).willReturn(false);

        assertBusinessError(
                OccupancyErrorCode.ALERT_NOT_FOUND,
                () -> vacancyAlertService.cancel(ALERT_ID, REQUESTER_USER_ID)
        );
    }

    /**
     * 소유 검사를 미리 읽어서 하지 않는다. 읽기와 삭제 사이에 발송이 끝나면 그 틈으로
     * 이미 발송된 신청의 이력이 지워진다 — 조건을 삭제문에 실어 틈 자체를 없앤다.
     */
    @Test
    @DisplayName("취소는 미리 읽지 않고 조건부 삭제 한 번으로 끝낸다.")
    void cancelDoesNotReadBeforeDeleting() {
        givenMemberships(membership(MEMBERSHIP_ID, COHORT_ID));
        given(alertRepository.deleteWaiting(ALERT_ID, Set.of(MEMBERSHIP_ID))).willReturn(true);

        vacancyAlertService.cancel(ALERT_ID, REQUESTER_USER_ID);

        verify(alertRepository, never()).lockWaitingById(anyLong());
        verify(alertRepository, never()).findWaitingByMembershipIds(anyCollection());
    }

    @Test
    @DisplayName("활성 소속이 없으면 취소할 수 있는 신청도 없다.")
    void cannotCancelWithoutActiveMembership() {
        givenMemberships();
        given(alertRepository.deleteWaiting(ALERT_ID, Set.of())).willReturn(false);

        assertBusinessError(
                OccupancyErrorCode.ALERT_NOT_FOUND,
                () -> vacancyAlertService.cancel(ALERT_ID, REQUESTER_USER_ID)
        );
    }

    // ────────────────────────────── 목록 ──────────────────────────────

    @Test
    @DisplayName("내 목록은 활성 소속 전체의 대기 중 신청을 기수와 함께 돌려준다.")
    void findMineReturnsWaitingAlertsAcrossAllMemberships() {
        givenMemberships(
                membership(MEMBERSHIP_ID, COHORT_ID),
                membership(OTHER_MEMBERSHIP_ID, OTHER_COHORT_ID));
        given(alertRepository.findWaitingByMembershipIds(anyCollection()))
                .willReturn(List.of(alert(MEMBERSHIP_ID), alert(OTHER_MEMBERSHIP_ID)));

        List<VacancyAlertView> result = vacancyAlertService.findMine(REQUESTER_USER_ID);

        assertThat(result).extracting(VacancyAlertView::cohortId)
                .containsExactly(COHORT_ID, OTHER_COHORT_ID);
        assertThat(result).allSatisfy(view ->
                assertThat(view.spaceId()).isEqualTo(SPACE_ID));
    }

    @Test
    @DisplayName("활성 소속이 없으면 조회하지 않고 빈 목록이다.")
    void findMineReturnsEmptyWithoutActiveMembership() {
        givenMemberships();

        assertThat(vacancyAlertService.findMine(REQUESTER_USER_ID)).isEmpty();

        verify(alertRepository, never()).findWaitingByMembershipIds(anyCollection());
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenOccupiedRoom() {
        givenOccupiedRoomOwnedByCohort(COHORT_ID);
    }

    private void givenOccupiedRoomOwnedByCohort(Long occupierCohortId) {
        given(occupancyQueryService.findActiveBySpaceIds(List.of(SPACE_ID), now()))
                .willReturn(Map.of(SPACE_ID, new SpaceOccupancyView(
                        OCCUPANCY_ID,
                        SPACE_ID,
                        now().plusHours(1),
                        occupierCohortId,
                        99L,
                        OCCUPIER_USER_ID,
                        List.of()
                )));
    }

    private void givenMemberships(CohortMembershipView... memberships) {
        given(cohortMembershipQueryService.findActiveMemberships(REQUESTER_USER_ID))
                .willReturn(List.of(memberships));
    }

    private CohortMembershipView membership(Long membershipId, Long cohortId) {
        return new CohortMembershipView(membershipId, cohortId, REQUESTER_USER_ID);
    }

    private VacancyAlert alert(Long membershipId) {
        VacancyAlert alert = VacancyAlert.request(SPACE_ID, membershipId, now());
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        return alert;
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
