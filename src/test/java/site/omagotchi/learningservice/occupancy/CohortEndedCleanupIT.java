package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.occupancy.application.CohortEndedCleanup;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.team.application.TeamService;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * 기수 종료 4단계 정리 (CE-01~04, 명세 08).
 *
 * <p><b>순서가 이 명세의 핵심이라 IT가 중심이다.</b> CE-02(알림 삭제)가 CE-03(점유 종료)보다
 * 먼저라는 계약은 관찰 가능한 결과로만 검증된다 — 종료 기수의 신청자는 공실 알림을 받지
 * 않고, 타 기수 대기자는 받는다. 그 발송이 실제 이벤트·리스너·DB를 타야 의미가 있다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class CohortEndedCleanupIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    VacancyAlertService vacancyAlertService;

    @Autowired
    TeamService teamService;

    @Autowired
    CohortEndedCleanup cohortEndedCleanup;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    /** CE-02 실패 시나리오에서 한 단계만 실패시키기 위한 Spy. */
    @MockitoSpyBean
    VacancyAlertService spiedVacancyAlertService;

    @BeforeEach
    void stubSuccessfulSendByDefault() {
        // sendVacancyAlert는 boolean을 반환하고 Mock 기본값은 false(건너뜀)이므로,
        // 명시적으로 true(발송 성공)를 스텁한다 — 아니면 CE-05 뒤 대기 알림이 소진되지
        // 않아 waitingAlertRows가 0이 되길 기다리는 awaitUntil이 타임아웃한다.
        given(vacancyAlertSender.sendVacancyAlert(any())).willReturn(true);
    }

    @Test
    @DisplayName("기수 종료는 팀·알림·점유·실습실을 한 번에 정리한다.")
    void cleansTeamsAlertsOccupanciesAndLabs() {
        Long cohortId = fixture.createCohort("기수종료-통합");
        OccupancyTestFixture.Member master = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "기수종료-통합-회의실", 8);
        Long labId = fixture.createLab(cohortId, "기수종료-통합-실습실", 30);
        Long teamId = teamService.create(cohortId, "기수종료-통합팀", master.userId()).teamId();

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        vacancyAlertService.request(roomId, null, waiter.userId());

        cohortEndedCleanup.cleanUp(cohortId);

        // CE-01 — 팀 소프트 삭제 + 팀원 물리 삭제
        assertThat(teamDeletedAt(teamId)).isNotNull();
        assertThat(teamMemberRows(teamId)).isZero();
        // CE-02 — 대기 신청 삭제
        assertThat(waitingAlertRows(roomId)).isZero();
        // CE-03 — RELEASED + 참여자 마감
        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(openParticipantRows(occupancyId)).isZero();
        // CE-04 — 유형을 가리지 않고 관리 주체를 해제한다. 실습실만 풀고 회의실을 남기면
        // 회의실이 종료 기수를 가리킨 채 동결된다 — 관리 권한이 그 기수 매니저를 요구하는데
        // 기수 종료로 그런 사람이 없어지기 때문이다. 상세 시나리오는
        // SpaceManagementLifecycleIT가 다룬다.
        assertThat(spaceCohortId(labId)).isNull();
        assertThat(spaceCohortId(roomId)).isNull();
    }

    /**
     * <b>CE-05의 존재 이유를 그대로 검증한다.</b> 알림 삭제가 점유 종료보다 먼저이므로,
     * 종료 기수의 신청자는 공실 알림을 받지 않는다. 회의실은 공유 자원이라 타 기수
     * 대기자에게는 <b>나가야 한다</b> (CE-03) — 안 나가면 그 사람은 빈 방을 계속 기다린다.
     */
    @Test
    @DisplayName("종료 기수 신청자는 공실 알림을 받지 않고, 타 기수 대기자는 받는다.")
    void endedCohortWaiterGetsNoAlertWhileOtherCohortWaiterDoes() {
        Long endedCohortId = fixture.createCohort("기수종료-순서-종료기수");
        Long survivingCohortId = fixture.createCohort("기수종료-순서-존속기수");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(endedCohortId);
        OccupancyTestFixture.Member endedWaiter = fixture.createActiveMember(endedCohortId);
        OccupancyTestFixture.Member survivingWaiter = fixture.createActiveMember(survivingCohortId);
        Long roomId = fixture.createMeetingRoom(endedCohortId, "기수종료-순서-회의실", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, endedWaiter.userId());
        vacancyAlertService.request(roomId, null, survivingWaiter.userId());

        cohortEndedCleanup.cleanUp(endedCohortId);

        // 타 기수 대기자에게 발송될 때까지 기다린다 (커밋 후 비동기).
        awaitUntil(() -> waitingAlertRows(roomId) == 0,
                "타 기수 대기자에게 공실 알림이 발송되지 않았습니다");

        ArgumentCaptor<VacancyAlertSender.VacancyNotice> captor =
                ArgumentCaptor.forClass(VacancyAlertSender.VacancyNotice.class);
        verify(vacancyAlertSender, atLeastOnce()).sendVacancyAlert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(VacancyAlertSender.VacancyNotice::recipientUserId)
                .containsExactly(survivingWaiter.userId())
                .doesNotContain(endedWaiter.userId());
    }

    /**
     * 명세 08 §5 "단계 부분 실패 — 성공 단계 유지"의 예외 하나를 고정한다. CE-02가
     * 실패하면 CE-03은 <b>격리라며 진행하지 않는다</b> — 진행하면 종료 기수 학생에게
     * 공실 알림이 나가는 순서 역전이 그대로 일어난다. 반면 기수 단위인 CE-04는
     * 앞 단계와 무관하게 진행된다.
     */
    @Test
    @DisplayName("알림 삭제가 실패하면 점유 종료는 건너뛰고 실습실 해제는 진행한다.")
    void skipsOccupancyReleaseWhenAlertDiscardFailsButStillUnassignsLabs() {
        Long cohortId = fixture.createCohort("기수종료-격리");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "기수종료-격리-회의실", 8);
        Long labId = fixture.createLab(cohortId, "기수종료-격리-실습실", 30);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        vacancyAlertService.request(roomId, null, waiter.userId());

        willThrow(new IllegalStateException("알림 삭제 실패"))
                .given(spiedVacancyAlertService).discardByMemberships(anyCollection());

        assertThatCode(() -> cohortEndedCleanup.cleanUp(cohortId)).doesNotThrowAnyException();

        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(waitingAlertRows(roomId)).isEqualTo(1);
        assertThat(spaceCohortId(labId)).isNull();
    }

    /**
     * 가장 밟기 쉬운 함정 — 종료 훅이 도는 시점에는 기수 파트가 멤버십을 이미 ENDED로
     * 바꿨을 수 있다. 활성으로 좁혀 조회하면 대상을 하나도 찾지 못한다.
     */
    @Test
    @DisplayName("멤버십이 이미 전부 종료된 뒤에 정리해도 대상을 찾는다.")
    void findsTargetsEvenWhenMembershipsAlreadyEnded() {
        Long cohortId = fixture.createCohort("기수종료-선종료");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "기수종료-선종료-회의실", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        vacancyAlertService.request(roomId, null, waiter.userId());

        endAllMemberships(cohortId);

        cohortEndedCleanup.cleanUp(cohortId);

        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(waitingAlertRows(roomId)).isZero();
    }

    /** 명세 08 §5 "훅 중복 수신 — 멱등". 각 단계가 조건부라 두 번째는 대상이 없다. */
    @Test
    @DisplayName("같은 기수를 두 번 정리해도 에러 없이 끝난다.")
    void isIdempotentAcrossRuns() {
        Long cohortId = fixture.createCohort("기수종료-멱등");
        OccupancyTestFixture.Member master = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "기수종료-멱등-회의실", 8);
        Long teamId = teamService.create(cohortId, "기수종료-멱등팀", master.userId()).teamId();

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        cohortEndedCleanup.cleanUp(cohortId);
        Object firstDeletedAt = teamDeletedAt(teamId);
        Object firstEndedAt = occupancyEndedAt(occupancyId);

        assertThatCode(() -> cohortEndedCleanup.cleanUp(cohortId)).doesNotThrowAnyException();

        // 두 번째 실행이 종료 사유·시각을 덮어쓰지 않는다.
        assertThat(teamDeletedAt(teamId)).isEqualTo(firstDeletedAt);
        assertThat(occupancyEndedAt(occupancyId)).isEqualTo(firstEndedAt);
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

    /** 점유·팀을 건드리지 않고 소속만 전부 끝낸다 — "종료 훅 시점의 상태"를 재현한다. */
    private void endAllMemberships(Long cohortId) {
        jdbcTemplate.update("""
                UPDATE learning_service.cohort_memberships
                   SET status = 'ENDED', ended_at = now()
                 WHERE cohort_id = ?
                """, cohortId);
    }

    private Object teamDeletedAt(Long teamId) {
        return jdbcTemplate.queryForObject("""
                SELECT deleted_at FROM learning_service.teams WHERE id = ?
                """, Object.class, teamId);
    }

    private int teamMemberRows(Long teamId) {
        return count("""
                SELECT count(*) FROM learning_service.team_members WHERE team_id = ?
                """, teamId);
    }

    private String occupancyStatus(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies WHERE id = ?
                """, String.class, occupancyId);
    }

    private Object occupancyEndedAt(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies WHERE id = ?
                """, Object.class, occupancyId);
    }

    private Long activeOccupancyId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Long.class, spaceId);
    }

    private int openParticipantRows(Long occupancyId) {
        return count("""
                SELECT count(*) FROM learning_service.occupancy_participants
                 WHERE occupancy_id = ? AND left_at IS NULL
                """, occupancyId);
    }

    private int waitingAlertRows(Long spaceId) {
        return count("""
                SELECT count(*) FROM learning_service.vacancy_alerts
                 WHERE space_id = ? AND notified_at IS NULL
                """, spaceId);
    }

    private Long spaceCohortId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT cohort_id FROM learning_service.spaces WHERE id = ?
                """, Long.class, spaceId);
    }

    private int count(String sql, Long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }
}
