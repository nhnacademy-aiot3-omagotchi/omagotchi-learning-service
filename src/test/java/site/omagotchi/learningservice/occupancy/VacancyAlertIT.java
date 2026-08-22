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
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertDispatcher;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
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

    /**
     * 실제 발송 수단이 아직 없다. Mock을 넣지 않으면 Dispatcher가 "sender 없음"으로
     * 건너뛰어 발송 경로가 통째로 검증되지 않는다 — 정상 반환이 곧 발송 성공이라는
     * Port 계약을 Mock의 기본 동작이 그대로 만족한다.
     */
    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

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

    @Test
    @DisplayName("반납이 먼저 커밋되면 신청은 빈 방으로 거절된다.")
    void requestAfterReleaseIsRejectedAsAvailable() {
        Long cohortId = fixture.createCohort("공실-반납경합");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-반납경합-1", 8);

        UUID waiterUserId = waiter.userId();
        roomOccupancyService.start(roomId, occupier.userId());
        roomOccupancyLifecycleService.release(roomId, occupier.userId());

        assertThatThrownBy(() -> vacancyAlertService.request(roomId, null, waiterUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_ROOM_AVAILABLE));

        assertThat(allRows(roomId)).isZero();
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
     * 발송과 취소가 겹치면 <b>발송은 나갔는데 이력이 사라지는</b> 구간이 생긴다.
     *
     * <p>취소가 잠금 없이 읽으면 아직 대기 중인 스냅샷을 보고 삭제로 진행한다. 그 DELETE는
     * 발송 트랜잭션이 커밋될 때까지 행 잠금에 막혀 있다가, 커밋된 뒤(= {@code notified_at}이
     * 찍힌 뒤) 그대로 실행된다 — {@code WHERE id = ?}뿐이라 그 사이 바뀐 상태를 보지 않는다.</p>
     *
     * <p>결과는 "알림은 받았는데 기록이 없다"이고, 명세 §3이 소진을 <b>행 보존</b>으로
     * 정해 둔 것과 어긋난다. 잠금으로 읽으면 커밋 뒤 조건을 다시 평가해
     * {@code notified_at IS NULL}에 걸리지 않으므로 404가 되어야 한다.</p>
     */
    @Test
    @DisplayName("발송 중 도착한 취소는 이력을 지우지 못하고 404가 된다.")
    void cancelDuringDispatchCannotEraseSentAlert() throws Exception {
        Long cohortId = fixture.createCohort("공실-발송중취소");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공실-발송중취소-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long alertId = vacancyAlertService.request(roomId, null, waiter.userId());

        CountDownLatch sendingStarted = new CountDownLatch(1);
        CountDownLatch cancelStarted = new CountDownLatch(1);

        // 발송 트랜잭션이 행 잠금을 쥔 채 머무는 구간을 만든다. 취소가 그 안에서
        // 읽기를 시도해야 두 경로가 실제로 겹친다.
        willAnswer(invocation -> {
            sendingStarted.countDown();
            cancelStarted.await(5, TimeUnit.SECONDS);
            Thread.sleep(300L);
            return null;
        }).given(vacancyAlertSender).sendVacancyAlert(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> dispatch = executor.submit(
                    () -> vacancyAlertDispatcher.dispatch(roomId, OffsetDateTime.now()));

            Future<Throwable> cancel = executor.submit(() -> {
                sendingStarted.await(5, TimeUnit.SECONDS);
                cancelStarted.countDown();
                return catchThrowable(() -> vacancyAlertService.cancel(alertId, waiter.userId()));
            });

            assertThat(dispatch.get(20, TimeUnit.SECONDS)).isEqualTo(1);
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
