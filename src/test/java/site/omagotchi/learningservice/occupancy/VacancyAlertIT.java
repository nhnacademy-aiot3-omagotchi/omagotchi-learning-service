package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertDispatcher;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.space.application.SpaceCommandService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 공실 알림 신청·취소·발송 (MR-02, MR-03, MR-15, MR-17, MR-34).
 *
 * <p>실제 PostgreSQL이 있어야 의미가 있다 — 중복 신청을 막는 것이 애플리케이션 조건이
 * 아니라 부분 유니크 인덱스({@code uq_vacancy_alerts_waiting})이고, 서비스에는 선검사가
 * 없기 때문이다. 인덱스가 사라지거나 이름이 바뀌면 여기서만 드러난다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class VacancyAlertIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @Autowired
    VacancyAlertService vacancyAlertService;

    @Autowired
    VacancyAlertDispatcher vacancyAlertDispatcher;

    @Autowired
    SpaceCommandService spaceCommandService;

    /**
     * 실제 발송 수단이 아직 없다. Mock을 넣지 않으면 Dispatcher가 "sender 없음"으로
     * 건너뛰어 발송 경로가 통째로 검증되지 않는다 — 정상 반환이 곧 발송 성공이라는
     * Port 계약을 Mock의 기본 동작이 그대로 만족한다.
     */
    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    /**
     * 잠금 지점을 가로채 순서를 제어하려고 감싼다. Mock이 아니라 Spy인 것이 중요하다 —
     * 대기 뒤에는 실제 잠금이 그대로 일어나야 이 검증이 의미를 갖는다.
     */
    @MockitoSpyBean
    RoomOccupancyRepository occupancyRepository;

    @Autowired
    VacancyAlertRepository alertRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    /** 잠금 직전에 멈출 Thread를 표시한다. 반납 Thread까지 멈추면 서로를 기다린다. */
    private static final ThreadLocal<Boolean> PAUSE_BEFORE_LOCK =
            ThreadLocal.withInitial(() -> false);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("사용 중인 회의실에 신청하면 대기 행이 남는다.")
    void requestPersistsWaitingRow() {
        Long cohortId = fixture.createCohort("공실-신청");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-신청-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long alertId = vacancyAlertService.request(roomId, null, waiter.userId());

        assertThat(alertId).isNotNull();
        assertThat(waitingRows(roomId)).isEqualTo(1);
    }

    /**
     * 선검사가 없으므로 이 409는 전적으로 부분 유니크와 그 변환에서 나온다.
     * 인덱스명이 바뀌면 {@code OccupancyConstraintTranslator}가 원본 예외를 그대로
     * 돌려주어 500이 되는데, 그 회귀를 여기서 잡는다.
     */
    @Test
    @DisplayName("같은 회의실에 두 번 신청하면 409다.")
    void duplicateRequestIsRejectedByPartialUnique() {
        Long cohortId = fixture.createCohort("공실-중복");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-중복-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        UUID waiterUserId = waiter.userId();
        vacancyAlertService.request(roomId, null, waiterUserId);

        assertThatThrownBy(() -> vacancyAlertService.request(roomId, null, waiterUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_ALREADY_REQUESTED));

        assertThat(waitingRows(roomId)).isEqualTo(1);
    }

    /**
     * 유니크가 대기 중에만 걸린다 (§4). 소진된 신청까지 막으면 한 번 알림을 받은 사람은
     * 그 방에 다시는 신청할 수 없다.
     */
    @Test
    @DisplayName("소진된 신청이 있어도 같은 회의실에 다시 신청할 수 있다.")
    void canRequestAgainAfterPreviousAlertWasNotified() {
        Long cohortId = fixture.createCohort("공실-재신청");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-재신청-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        markAllNotified(roomId);

        assertThatCode(() -> vacancyAlertService.request(roomId, null, waiter.userId()))
                .doesNotThrowAnyException();

        assertThat(waitingRows(roomId)).isEqualTo(1);
        assertThat(allRows(roomId)).isEqualTo(2);
    }

    @Test
    @DisplayName("취소하면 행이 물리 삭제되고 다시 신청할 수 있다.")
    void cancelDeletesRowAndFreesRequestAgain() {
        Long cohortId = fixture.createCohort("공실-취소");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-취소-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long alertId = vacancyAlertService.request(roomId, null, waiter.userId());

        vacancyAlertService.cancel(alertId, waiter.userId());

        assertThat(allRows(roomId)).isZero();
        assertThatCode(() -> vacancyAlertService.request(roomId, null, waiter.userId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("남의 신청은 취소할 수 없고 행도 그대로다.")
    void cannotCancelSomeoneElsesAlert() {
        Long cohortId = fixture.createCohort("공실-타인취소");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member stranger = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-타인취소-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long alertId = vacancyAlertService.request(roomId, null, waiter.userId());
        UUID strangerUserId = stranger.userId();

        assertThatThrownBy(() -> vacancyAlertService.cancel(alertId, strangerUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_NOT_FOUND));

        assertThat(waitingRows(roomId)).isEqualTo(1);
    }

    @Test
    @DisplayName("내 목록에는 내 대기 중 신청만 담긴다.")
    void findMineReturnsOnlyOwnWaitingAlerts() {
        Long cohortId = fixture.createCohort("공실-내목록");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member other = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-내목록-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        vacancyAlertService.request(roomId, null, other.userId());

        List<VacancyAlertView> mine = vacancyAlertService.findMine(waiter.userId());

        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().spaceId()).isEqualTo(roomId);
        assertThat(mine.getFirst().cohortId()).isEqualTo(cohortId);
    }

    @Test
    @DisplayName("점유자 본인은 신청할 수 없다.")
    void occupierCannotRequestForOwnRoom() {
        Long cohortId = fixture.createCohort("공실-본인방");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-본인방-1", 8);

        UUID occupierUserId = occupier.userId();
        roomOccupancyService.start(roomId, occupierUserId);

        assertThatThrownBy(() -> vacancyAlertService.request(roomId, null, occupierUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_OCCUPIER_CANNOT_REQUEST));

        assertThat(allRows(roomId)).isZero();
    }

    @Test
    @DisplayName("점유가 없는 회의실에는 신청할 수 없다.")
    void cannotRequestForAvailableRoom() {
        Long cohortId = fixture.createCohort("공실-빈방");
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-빈방-1", 8);
        UUID waiterUserId = waiter.userId();

        assertThatThrownBy(() -> vacancyAlertService.request(roomId, null, waiterUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_ROOM_AVAILABLE));
    }

    /**
     * <b>신청과 반납의 교차 실행을 결정적으로 재현한다.</b>
     *
     * <p>순서를 잠금 지점에서 직접 제어한다 — 신청 Transaction이 {@code lockById} <b>직전에</b>
     * 멈추고, 그 사이 반납이 같은 행을 잠그고 커밋한 뒤, 신청을 재개시킨다. 간격을
     * {@code Thread.sleep}으로 기대하지 않으므로 CI에서도 순서가 뒤집히지 않는다.</p>
     *
     * <p>잠그지 않던 시절에는 이 순서에서 신청이 <b>받아들여졌다.</b> 요약 조회가 반납 이전
     * 스냅샷을 보고 통과시켰고, 반납의 {@code AFTER_COMMIT} 발송은 아직 커밋되지 않은 그
     * 신청을 보지 못해 <b>이미 빈 방에 대기 신청</b>이 남았다 — 그 방이 다시 차고 비워질
     * 때까지 발송되지 않는다.</p>
     *
     * <p>이 테스트가 성립하는 전제가 하나 더 있다 — 신청이 요약을 <b>값으로</b> 읽는다는 것.
     * 엔티티로 읽으면 1차 캐시에 올라가 {@code lockById}가 반납 이전 스냅샷을 돌려주고,
     * 잠금을 얻고도 ACTIVE로 판정한다.</p>
     */
    @Test
    @DisplayName("신청이 잠금을 기다리는 사이 반납이 커밋되면 빈 방으로 거절된다.")
    void requestBlockedAtLockSeesReleaseCommittedMeanwhile() throws Exception {
        Long cohortId = fixture.createCohort("공실-잠금경합");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-잠금경합-1", 8);
        UUID waiterUserId = waiter.userId();

        roomOccupancyService.start(roomId, occupier.userId());

        CountDownLatch reachedLock = new CountDownLatch(1);
        CountDownLatch releaseCommitted = new CountDownLatch(1);

        // 신청 Thread에서만 멈춘다. 반납도 같은 Method로 행을 잠그므로, 구분하지 않으면
        // 반납까지 멈춰 서로를 기다린다.
        willAnswer(invocation -> {
            if (PAUSE_BEFORE_LOCK.get()) {
                reachedLock.countDown();
                releaseCommitted.await(10, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).given(occupancyRepository).lockById(anyLong());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> request = executor.submit(() -> {
                PAUSE_BEFORE_LOCK.set(true);
                try {
                    return catchThrowable(
                            () -> vacancyAlertService.request(roomId, null, waiterUserId));
                } finally {
                    PAUSE_BEFORE_LOCK.remove();
                }
            });

            assertThat(reachedLock.await(10, TimeUnit.SECONDS)).isTrue();
            roomOccupancyLifecycleService.release(roomId, occupier.userId());
            releaseCommitted.countDown();

            assertThat(request.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                            .isEqualTo(OccupancyErrorCode.ALERT_ROOM_AVAILABLE));
        } finally {
            executor.shutdownNow();
        }

        // 이미 빈 방을 기다리는 신청이 남으면 안 된다.
        assertThat(allRows(roomId)).isZero();
    }

    // ────────────────────────────── 공간 비활성화 정리 (RM-15) ──────────────────────────────

    /**
     * 비활성화와 <b>같은 Transaction</b>에서 지운다 (명세 04 §2). 나누면 비활성 공간에
     * 신청이 남아, 그 방이 다시 활성화될 때까지 아무 일도 일어나지 않는 신청이 된다.
     */
    @Test
    @DisplayName("공간을 비활성화하면 그 공간의 대기 신청이 함께 삭제된다.")
    void deactivatingSpaceDiscardsWaitingAlerts() {
        Long cohortId = fixture.createCohort("공실-비활성화");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-비활성화-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        // 반납이 아니라 만료로 비운다. 반납은 커밋 후 비동기 발송을 트리거해 신청이
        // 소진되므로, 이 테스트가 보려는 "대기 중 신청"이 남지 않는다.
        expireOccupancy(roomId);

        spaceCommandService.deactivate(roomId, "설비 점검", manager.userId());

        assertThat(waitingRows(roomId)).isZero();
        assertThat(spaceStatus(roomId)).isEqualTo("INACTIVE");
    }

    /** 소진된 신청은 이력이다 (명세 04 §3) — 지우면 "알림을 보낸 적 있다"를 잃는다. */
    @Test
    @DisplayName("이미 발송된 신청은 비활성화로 지워지지 않는다.")
    void deactivatingSpaceKeepsAlreadyNotifiedAlerts() {
        Long cohortId = fixture.createCohort("공실-비활성화이력");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-비활성화이력-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        markAllNotified(roomId);
        expireOccupancy(roomId);

        spaceCommandService.deactivate(roomId, "설비 점검", manager.userId());

        assertThat(allRows(roomId)).isEqualTo(1);
        assertThat(waitingRows(roomId)).isZero();

        // 소진된 행의 (구)신청자에게 취소 통보가 가면 안 된다 — 방금 공실 알림을 받은
        // 사람이 "신청이 취소됐다"는 안내를 받게 된다. 수신자가 삭제 결과(RETURNING)에서
        // 나오므로 지워지지 않은 행은 목록에 오를 수 없다.
        verify(vacancyAlertSender, never()).sendDiscardNotice(any());
    }

    /** 다른 공간의 신청까지 지우면 무관한 사람들이 조용히 대기에서 빠진다. */
    @Test
    @DisplayName("다른 공간의 대기 신청은 건드리지 않는다.")
    void deactivatingSpaceLeavesOtherSpacesAlertsUntouched() {
        Long cohortId = fixture.createCohort("공실-비활성화범위");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member otherOccupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-비활성화범위-1", 8);
        Long otherRoomId = fixture.createMeetingRoom(cohortId, "공실-비활성화범위-2", 8);

        roomOccupancyService.start(otherRoomId, otherOccupier.userId());
        vacancyAlertService.request(otherRoomId, null, waiter.userId());
        roomOccupancyService.start(roomId, occupier.userId());
        expireOccupancy(roomId);

        spaceCommandService.deactivate(roomId, "설비 점검", manager.userId());

        assertThat(waitingRows(otherRoomId)).isEqualTo(1);
    }

    /**
     * 통보가 필요한 유일한 삭제 경로다 (명세 04 §3) — 사용자가 인지하지 못한 채 알림이
     * 사라지기 때문이다. 본인 취소·강제 종료·기수 종료는 통보하지 않는다.
     */
    @Test
    @DisplayName("비활성화로 삭제된 신청의 (구)신청자에게 통보가 발송된다.")
    void notifiesFormerApplicantsWhenSpaceIsDeactivated() {
        Long cohortId = fixture.createCohort("공실-삭제통보");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        String roomName = "공실-삭제통보-1";
        Long roomId = fixture.createMeetingRoom(cohortId, roomName, 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        expireOccupancy(roomId);

        spaceCommandService.deactivate(roomId, "설비 점검", manager.userId());

        ArgumentCaptor<VacancyAlertSender.DiscardNotice> captor =
                ArgumentCaptor.forClass(VacancyAlertSender.DiscardNotice.class);
        awaitUntil(() -> discardNoticeCount() == 1, "삭제 통보가 발송되지 않았습니다");
        verify(vacancyAlertSender).sendDiscardNotice(captor.capture());

        VacancyAlertSender.DiscardNotice notice = captor.getValue();
        assertThat(notice.spaceId()).isEqualTo(roomId);
        assertThat(notice.spaceName()).isEqualTo(roomName);
        assertThat(notice.recipientUserId()).isEqualTo(waiter.userId());
    }

    /** 지운 신청이 없으면 이벤트를 내지 않는다 — 아무에게도 안 보낼 일에 조회를 더하지 않는다. */
    @Test
    @DisplayName("지운 신청이 없으면 통보하지 않는다.")
    void sendsNoDiscardNoticeWhenNothingWasDiscarded() {
        Long cohortId = fixture.createCohort("공실-삭제통보없음");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-삭제통보없음-1", 8);

        spaceCommandService.deactivate(roomId, "설비 점검", manager.userId());

        assertThat(spaceStatus(roomId)).isEqualTo("INACTIVE");
        verify(vacancyAlertSender, never()).sendDiscardNotice(any());
    }

    /**
     * <b>비활성화가 롤백되면 아무 일도 없었어야 한다</b> — 신청은 남고, 통보는 나가지 않는다.
     *
     * <p>롤백 자체는 Spring이 하지만, 이 테스트가 지키는 것은 우리 설정 둘이다.
     * {@code discardBySpace}가 {@code REQUIRED}로 비활성화 Transaction에 합류한다는 것
     * (주변의 {@code REQUIRES_NEW}들을 따라 떼어내면 <b>비활성화는 롤백됐는데 신청만
     * 지워진다</b>), 그리고 리스너가 {@code AFTER_COMMIT}이라는 것 (일반
     * {@code @EventListener}로 바꾸면 롤백된 삭제의 통보가 발송된다). 둘 다 코드에 이유가
     * 적혀 있지만, 어기면 깨지는 것은 이 테스트뿐이다.</p>
     */
    @Test
    @DisplayName("비활성화가 롤백되면 신청이 남고 삭제 통보도 나가지 않는다.")
    void rolledBackDeactivationKeepsAlertsAndSendsNoNotice() throws Exception {
        Long cohortId = fixture.createCohort("공실-비활성화롤백");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-비활성화롤백-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        expireOccupancy(roomId);
        UUID managerUserId = manager.userId();

        transactionTemplate.executeWithoutResult(status -> {
            spaceCommandService.deactivate(roomId, "설비 점검", managerUserId);
            status.setRollbackOnly();
        });

        assertThat(spaceStatus(roomId)).isEqualTo("ACTIVE");
        assertThat(waitingRows(roomId)).isEqualTo(1);

        // 부재는 기다릴 수 없고 표본만 뜰 수 있다 — 잘못 발행된 이벤트라면 @Async 실행기가
        // 곧바로 집어 가므로, 짧게 가라앉힌 뒤 확인한다. 순서를 기대하는 sleep이 아니다.
        Thread.sleep(300L);
        verify(vacancyAlertSender, never()).sendDiscardNotice(any());
    }

    // ────────────────────────────── 발송 ──────────────────────────────

    @Test
    @DisplayName("발송하면 대기 행이 소진되고 이력은 남는다.")
    void dispatchConsumesWaitingRowsWithoutDeletingThem() {
        Long cohortId = fixture.createCohort("공실-발송");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-발송-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        assertThat(vacancyAlertDispatcher.dispatch(roomId, OffsetDateTime.now())).isEqualTo(1);

        assertThat(waitingRows(roomId)).isZero();
        assertThat(allRows(roomId)).isEqualTo(1);
    }

    /**
     * 이름이 DB에서 실제로 실려 나가는지 확인한다.
     *
     * <p>단위 테스트는 {@code SpaceNameQueryService}를 Mock으로 두므로 조회가 실제로
     * 무엇을 돌려주는지 보지 못한다. {@code findNameById}에 조건이 하나 붙거나(예:
     * {@code deleted_at IS NULL}) 컬럼이 바뀌면 조용히 대체 문구로 내려앉는데,
     * 그 회귀는 실제 DB가 있어야 드러난다.</p>
     */
    @Test
    @DisplayName("알림에는 공간 식별자가 아니라 실제 이름이 실린다.")
    void noticeCarriesActualSpaceName() {
        Long cohortId = fixture.createCohort("공실-이름");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        String roomName = "공실-이름-1";
        Long roomId = fixture.createMeetingRoom(cohortId, roomName, 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        OffsetDateTime vacatedAt = OffsetDateTime.now();

        assertThat(vacancyAlertDispatcher.dispatch(roomId, vacatedAt)).isEqualTo(1);

        ArgumentCaptor<VacancyAlertSender.VacancyNotice> captor =
                ArgumentCaptor.forClass(VacancyAlertSender.VacancyNotice.class);
        verify(vacancyAlertSender).sendVacancyAlert(captor.capture());

        VacancyAlertSender.VacancyNotice notice = captor.getValue();
        assertThat(notice.spaceId()).isEqualTo(roomId);
        assertThat(notice.spaceName()).isEqualTo(roomName);
        assertThat(notice.recipientUserId()).isEqualTo(waiter.userId());
        assertThat(notice.vacatedAt()).isEqualTo(vacatedAt);
    }

    /** 회의실은 공유 자원이라 타 기수 대기자에게도 알림이 가야 한다 (MR-34, CE-03). */
    @Test
    @DisplayName("타 기수 신청자에게도 발송한다.")
    void dispatchReachesApplicantsFromOtherCohorts() {
        Long occupierCohortId = fixture.createCohort("공실-타기수-점유");
        Long waiterCohortId = fixture.createCohort("공실-타기수-대기");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(occupierCohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(waiterCohortId);
        Long roomId = fixture.createMeetingRoom(occupierCohortId, "공실-타기수-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        assertThat(vacancyAlertDispatcher.dispatch(roomId, OffsetDateTime.now())).isEqualTo(1);
        assertThat(waitingRows(roomId)).isZero();
    }

    @Test
    @DisplayName("두 번 발송해도 이미 소진된 신청은 다시 보내지 않는다.")
    void secondDispatchFindsNoCandidates() {
        Long cohortId = fixture.createCohort("공실-발송멱등");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-발송멱등-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        assertThat(vacancyAlertDispatcher.dispatch(roomId, OffsetDateTime.now())).isEqualTo(1);
        assertThat(vacancyAlertDispatcher.dispatch(roomId, OffsetDateTime.now())).isZero();
    }

    /**
     * <b>배선 자체를 확인한다.</b> 발송 규칙이 아무리 맞아도 이벤트가 리스너에 닿지 않으면
     * 아무 일도 일어나지 않는다 — {@code @EnableAsync} 누락, 잘못된 {@code TransactionPhase},
     * 리스너가 Bean으로 등록되지 않은 경우가 전부 조용히 실패한다.
     *
     * <p>{@code @Async}라 커밋 직후 다른 Thread에서 돌기 때문에 결과를 기다린다. 발송 규칙
     * 자체는 위 테스트들이 동기로 고정하므로, 여기서 보는 것은 "닿는가" 하나다.</p>
     */
    @Test
    @DisplayName("반납하면 이벤트가 공실 알림 발송까지 이어진다.")
    void releaseEventReachesVacancyAlertDispatch() {
        Long cohortId = fixture.createCohort("공실-배선");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-배선-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        roomOccupancyLifecycleService.release(roomId, occupier.userId());

        awaitUntil(() -> waitingRows(roomId) == 0,
                "반납 이벤트가 공실 알림 발송으로 이어지지 않았습니다");
        assertThat(allRows(roomId)).isEqualTo(1);
    }

    /**
     * <b>발송이 잠금을 쥔 사이 도착한 취소는 이력을 지우지 못한다.</b>
     *
     * <p>순서를 <b>테스트가 잠금을 직접 쥐어</b> 만든다. 잠금을 놓지 않는 동안 취소의 삭제는
     * <b>반드시</b> 막혀 있으므로, 간격을 {@code Thread.sleep}으로 기대할 필요가 없다 —
     * 아래 짧은 타임아웃은 "아직 막혀 있나"를 확인하는 탐침이지 순서를 만드는 장치가 아니다.</p>
     *
     * <p>취소가 먼저 읽고 지우던 시절에는 이 순서에서 <b>이미 발송된 신청이 지워졌다.</b>
     * 삭제문이 {@code WHERE id = ?}뿐이라 잠금이 풀린 뒤 바뀐 상태를 보지 못했다. 지금은
     * {@code notified_at IS NULL}을 함께 걸어, 잠금이 풀리면 조건을 다시 평가해 0행이 된다.</p>
     *
     * <p>결과가 "알림은 받았는데 기록이 없다"가 아니라 <b>404 + 행 보존</b>이어야 한다.
     * 명세 04 §3이 소진을 행 보존으로 정해 두었다.</p>
     */
    @Test
    @DisplayName("발송이 잠금을 쥔 사이 도착한 취소는 이력을 지우지 못하고 404가 된다.")
    void cancelBlockedByDeliveryLockCannotEraseSentAlert() throws Exception {
        Long cohortId = fixture.createCohort("공실-발송중취소");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-발송중취소-1", 8);
        UUID waiterUserId = waiter.userId();

        roomOccupancyService.start(roomId, occupier.userId());
        Long alertId = vacancyAlertService.request(roomId, null, waiterUserId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> cancel = transactionTemplate.execute(status -> {
                // 발송 Transaction이 하는 것과 같은 잠금이다. 이 블록이 커밋될 때까지
                // 다른 Transaction의 삭제는 이 행에서 진행할 수 없다.
                VacancyAlert locked = alertRepository.lockWaitingById(alertId).orElseThrow();

                Future<Throwable> attempt = executor.submit(
                        () -> catchThrowable(() -> vacancyAlertService.cancel(alertId, waiterUserId)));

                // 잠금을 쥔 동안에는 반드시 막혀 있다 — 타임아웃이 나야 정상이다.
                assertThatThrownBy(() -> attempt.get(1, TimeUnit.SECONDS))
                        .isInstanceOf(TimeoutException.class);

                // 발송 성공에 해당하는 기록. 커밋되면 막혀 있던 삭제가 재개된다.
                locked.markNotified(OffsetDateTime.now());
                return attempt;
            });

            assertThat(cancel.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                            .isEqualTo(OccupancyErrorCode.ALERT_NOT_FOUND));
        } finally {
            executor.shutdownNow();
        }

        // 발송된 신청은 이력으로 남는다 — 지워지면 "알림을 보낸 적 있다"를 잃는다.
        assertThat(allRows(roomId)).isEqualTo(1);
        assertThat(waitingRows(roomId)).isZero();
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void awaitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(message);
    }

    private int waitingRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.vacancy_alerts
                 WHERE space_id = ? AND notified_at IS NULL
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    /**
     * 만료 시각을 과거로 밀어 "사용 중"에서 뺀다.
     *
     * <p>비활성화는 활성 점유가 없어야 하는데(RM-12), 반납으로 비우면 커밋 후 비동기
     * 발송이 신청을 소진시켜 이 테스트가 보려는 대기 신청이 남지 않는다. 만료는
     * {@code existsActive}가 {@code expires_at}으로 판정하므로 스케줄러 없이도 즉시
     * 반영되고, 발송을 트리거하지도 않는다.</p>
     */
    private void expireOccupancy(Long spaceId) {
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET started_at = now() - interval '3 hours',
                       expires_at = now() - interval '1 minute'
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, spaceId);
    }

    /** Mock에 쌓인 삭제 통보 호출 수. 비동기 발송이 끝났는지 기다리는 데 쓴다. */
    private int discardNoticeCount() {
        return org.mockito.Mockito.mockingDetails(vacancyAlertSender).getInvocations().stream()
                .filter(invocation -> "sendDiscardNotice".equals(invocation.getMethod().getName()))
                .toList()
                .size();
    }

    private String spaceStatus(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.spaces WHERE id = ?
                """, String.class, spaceId);
    }

    private int allRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.vacancy_alerts WHERE space_id = ?
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private void markAllNotified(Long spaceId) {
        jdbcTemplate.update("""
                UPDATE learning_service.vacancy_alerts
                   SET notified_at = now()
                 WHERE space_id = ? AND notified_at IS NULL
                """, spaceId);
    }
}
