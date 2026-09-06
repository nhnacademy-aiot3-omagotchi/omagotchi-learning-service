package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.application.result.CohortResponse;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipOccupancySweep;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.team.application.TeamService;

import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 기수 종료 훅의 트리거 (명세 08 §7 "트리거 방식"·"종료 판정 기준").
 *
 * <p>정리 <b>내용</b>은 {@code CohortEndedCleanupIT}가 다룬다. 여기는 관리자의 상태 전이
 * 하나가 실제로 그 정리에 닿는지, 그리고 <b>닿기 전에 대상이 정지되는지</b>를 본다 —
 * 명세 §5는 "종료 후 잔여 요청 → 활성 멤버십이 없으므로 403"을 전제하는데, 소속을 함께
 * 끝내지 않으면 그 전제가 아예 성립하지 않는다.</p>
 *
 * <p><b>정지는 이제 점유 시작까지 덮는다.</b> 활성 소속을 보는 경로(공실 신청·팀·참여자)에
 * 더해, 재실만 보던 점유 시작(MR-22)도 소속 잠금으로 막힌다 —
 * {@code endedMembershipCannotStartOccupancy}. 그래도 종료 직전에 시작된 점유와 훅 유실은
 * 남으므로 멤버십 스윕이 받침으로 유지된다 —
 * {@code lingeringOccupancyFromLostHookIsSweptByMembershipSweep}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class CohortClosedTriggerIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    CohortService cohortService;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    RoomOccupancyLifecycleService lifecycleService;

    @Autowired
    VacancyAlertService vacancyAlertService;

    @Autowired
    EndedMembershipOccupancySweep occupancySweep;

    @Autowired
    TeamService teamService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    @BeforeEach
    void stubSuccessfulSendByDefault() {
        // sendVacancyAlert는 boolean을 반환하고 Mock 기본값은 false(건너뜀)이므로,
        // 명시적으로 true(발송 성공)를 스텁한다 — 발송이 실제로 일어나야 하는 시나리오에서
        // waitingAlertRows가 0이 되길 기다리는 awaitUntil이 타임아웃하지 않게 한다.
        given(vacancyAlertSender.sendVacancyAlert(any())).willReturn(true);
    }

    /** 멤버십별 이벤트가 나가지 않는 것을 직접 본다 — CE-05를 지키는 결정이다. */
    @MockitoSpyBean
    CohortEventPublisher eventPublisher;

    /**
     * 상태 전이와 소속 종료가 <b>한 트랜잭션</b>이라는 계약.
     *
     * <p>소속 종료는 {@code clearAutomatically} 벌크 UPDATE라 영속성 컨텍스트를 비운다.
     * 짝인 {@code flushAutomatically}가 빠지면 바로 앞의 상태 변경이 조용히 버려진다 —
     * 실제로 빼서 확인했고 {@code CLOSED} 기대에 {@code ACTIVE}가 관측됐다. 응답 검증까지
     * 함께 두는 것은 그때 분리된 엔티티로 응답을 만들기 때문이다.</p>
     */
    @Test
    @DisplayName("기수를 종료하면 상태와 소속이 함께 커밋된다.")
    void closesCohortAndItsMembershipsInOneTransaction() {
        Long cohortId = fixture.createCohort("트리거-트랜잭션");
        fixture.createActiveMember(cohortId);
        fixture.createActiveStudent(cohortId);

        activate(cohortId);
        CohortResponse response = close(cohortId);

        assertThat(response.status()).isEqualTo(CohortStatus.CLOSED);
        assertThat(cohortStatus(cohortId)).isEqualTo("CLOSED");
        assertThat(membershipRows(cohortId, "ACTIVE")).isZero();
        assertThat(membershipRows(cohortId, "ENDED")).isEqualTo(2);
    }

    /**
     * <b>명세 §5의 전제를 세우는 테스트다.</b> 소속을 함께 끝내지 않으면 활성 멤버십 판정이
     * 기수 상태를 보지 않으므로, 종료된 기수 학생이 계속 공실 알림을 신청한다 — 방금 지운
     * 신청이 정리가 끝나기도 전에 다시 쌓인다.
     */
    @Test
    @DisplayName("종료된 기수의 학생은 공실 알림을 신청할 수 없다.")
    void endedCohortMemberCannotRequestVacancyAlert() {
        Long cohortId = fixture.createCohort("트리거-잔여요청");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member student = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "트리거-잔여요청-회의실", 8);

        roomOccupancyService.start(roomId, occupier.userId());

        activate(cohortId);
        close(cohortId);

        assertThatThrownBy(() -> vacancyAlertService.request(roomId, null, student.userId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.ALERT_COHORT_ACCESS_DENIED));
        assertThat(waitingAlertRows(roomId)).isZero();
    }

    /**
     * <b>예전에는 여기가 구멍이었다.</b> 점유 시작이 활성 소속이 아니라 <b>재실</b>만 봤기
     * 때문에(MR-22), 종료 시점에 출근 중이던 학생은 정리가 도는 동안 새 점유를 시작할 수
     * 있었고 그 점유는 CE-03의 대상 조회를 이미 지나쳐 잔존했다. 멤버십 스윕이 뒤늦게
     * 받치는 것이 유일한 방어였다.
     *
     * <p>지금은 두 겹으로 막힌다 — 종료 훅이 체류 구간을 마감해 재실 자체가 사라지고,
     * 점유 시작이 소속 행을 잠근 뒤 ACTIVE를 재확인한다. 이 테스트가 보는 것은 후자다.
     * 소속 잠금은 종료의 벌크 UPDATE와 같은 행에서 직렬화되므로, "재실 조회는 통과했는데
     * 그 사이 종료가 커밋된" 경합에서도 결과가 갈리지 않는다.</p>
     */
    @Test
    @DisplayName("종료된 소속은 재실이 남아 있어도 점유를 시작할 수 없다.")
    void endedMembershipCannotStartOccupancy() {
        Long cohortId = fixture.createCohort("트리거-재실구멍");
        fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member student = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "트리거-재실구멍-회의실", 8);

        // 비동기 기수 종료 훅이 체류를 먼저 닫으면 NOT_PRESENT가 되어 검증 결과가 실행
        // 타이밍에 따라 달라진다. 소속만 직접 끝내 열린 체류를 결정적으로 남긴다.
        endMembership(student.membershipId());

        assertThatThrownBy(() -> roomOccupancyService.start(roomId, student.userId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.MEMBERSHIP_NOT_ACTIVE));
    }

    /**
     * <b>스윕은 여전히 필요하다.</b> 점유 시작이 막히면서 "종료 후 시작된 점유"는 더 이상
     * 생기지 않지만, 종료 <b>직전</b>에 시작돼 CE-03의 대상 조회를 지나친 점유나 훅 자체가
     * 유실된 경우의 잔여는 그대로 남는다. 그 받침이 아직 성립하는지를 고정한다.
     *
     * <p>훅 유실은 {@code staleAlertIsDiscardedInsteadOfSentWhenHookWasLost}와 같은 방식으로
     * 소속만 끝내 재현한다 — 정리가 돌지 않은 것과 관측 가능한 상태가 같다.</p>
     */
    @Test
    @DisplayName("훅이 유실돼 남은 점유는 멤버십 스윕이 정리한다.")
    void lingeringOccupancyFromLostHookIsSweptByMembershipSweep() {
        Long cohortId = fixture.createCohort("트리거-점유잔여");
        fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member student = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "트리거-점유잔여-회의실", 8);

        roomOccupancyService.start(roomId, student.userId());
        Long occupancyId = activeOccupancyId(roomId);

        // 훅이 돌지 않은 채 소속만 끝난 상태 — 점유가 ACTIVE로 남는다.
        endMembership(student.membershipId());
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");

        occupancySweep.sweep(100);

        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(openParticipantRows(occupancyId)).isZero();
    }

    /**
     * <b>이 잔여를 치울 주체는 발송 경로뿐이다.</b> 정리 훅이 유실되면 종료된 소속의 대기
     * 알림이 남는데, 멤버십 정합성 스윕은 <b>열린 참여 행</b>을 커서로 돌기 때문에 점유도
     * 참여도 없이 신청만 남은 사람은 방문하지 않는다. 그 상태에서 타 기수 사람이 방을
     * 반납하면 <b>서비스를 떠난 사람에게 알림이 간다</b> — 실제로 재현해 확인했던 경로다.
     *
     * <p>훅 유실은 소속만 끝내 재현한다. 정리가 돌지 않은 것과 관측 가능한 상태가 같다.</p>
     */
    @Test
    @DisplayName("정리 훅이 유실돼도 소속이 끝난 신청자에게는 발송하지 않는다.")
    void staleAlertIsDiscardedInsteadOfSentWhenHookWasLost() {
        Long cohortId = fixture.createCohort("트리거-알림잔여");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "트리거-알림잔여-회의실", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        // 훅이 돌지 않은 채 소속만 끝난 상태 — 대기 알림이 그대로 남는다.
        endMembership(waiter.membershipId());
        assertThat(waitingAlertRows(roomId)).isEqualTo(1);

        lifecycleService.release(roomId, occupier.userId());

        awaitUntil(() -> waitingAlertRows(roomId) == 0, "잔여 신청이 폐기되지 않았습니다");
        verify(vacancyAlertSender, never()).sendVacancyAlert(any());
    }

    /**
     * 트리거가 실제로 6단계에 닿는지 — 관리자 명령 하나로 팀·알림·점유·출결·실습실이 모두
     * 정리돼야 한다. 각 단계의 세부 규칙은 {@code CohortEndedCleanupIT}가 본다.
     */
    @Test
    @DisplayName("기수 종료 한 번으로 팀·알림·점유·출결·실습실이 모두 정리된다.")
    void oneStatusChangeDrivesEveryCleanupStep() {
        Long cohortId = fixture.createCohort("트리거-전체정리");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveStudent(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "트리거-전체정리-회의실", 8);
        Long labId = fixture.createLab(cohortId, "트리거-전체정리-실습실", 30);
        Long teamId = teamService.create(cohortId, "트리거-전체정리팀", manager.userId()).teamId();

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        vacancyAlertService.request(roomId, null, waiter.userId());

        activate(cohortId);
        close(cohortId);

        awaitUntil(() -> spaceCohortId(roomId) == null, "기수 종료가 공간 관리 주체 해제까지 닿지 않았습니다");

        assertThat(teamDeletedAt(teamId)).isNotNull();
        assertThat(waitingAlertRows(roomId)).isZero();
        assertThat(openParticipantRows(occupancyId)).isZero();
        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(attendanceStatus(manager.membershipId())).isEqualTo("MISSING_CHECK_OUT");
        assertThat(attendanceStatus(occupier.membershipId())).isEqualTo("MISSING_CHECK_OUT");
        assertThat(attendanceStatus(waiter.membershipId())).isEqualTo("MISSING_CHECK_OUT");
        assertThat(openPresenceRows(cohortId)).isZero();
        assertThat(latestPresenceEndedAt(waiter.membershipId()))
                .isEqualTo(membershipEndedAt(waiter.membershipId()));
        assertThat(spaceCohortId(labId)).isNull();
        // 회의실도 유형을 가리지 않고 관리 주체가 해제된다 — 그렇지 않으면 종료 기수를
        // 가리킨 채 동결된다. 인수·삭제 순환은 SpaceManagementLifecycleIT가 다룬다.
        assertThat(spaceCohortId(roomId)).isNull();
    }

    /**
     * <b>이 검증이 CE-05를 구조적으로 지킨다.</b> 소속을 끝내면서 멤버십별 이벤트까지
     * 발행하면 팬아웃이 대기 알림 삭제보다 먼저 점유를 종료시켜 종료 기수 학생에게 공실
     * 알림이 나가고, 팀도 통째 해체(CE-01) 대신 자동 위임(GR-16)으로 정리된다.
     */
    @Test
    @DisplayName("기수 종료는 멤버십별 종료 이벤트를 발행하지 않는다.")
    void doesNotFanOutPerMembershipEvents() {
        Long cohortId = fixture.createCohort("트리거-팬아웃금지");
        fixture.createActiveMember(cohortId);
        fixture.createActiveStudent(cohortId);

        activate(cohortId);
        close(cohortId);

        verify(eventPublisher, never()).publishMembershipEnded(any());
    }

    /** 종료는 ACTIVE에서만 가능하다 — 실패한 전이가 훅을 깨우면 안 된다. */
    @Test
    @DisplayName("종료할 수 없는 상태면 훅을 발행하지 않는다.")
    void doesNotPublishWhenTransitionIsRejected() {
        Long cohortId = fixture.createCohort("트리거-거부");
        fixture.createActiveMember(cohortId);

        // PREPARING → CLOSED는 허용되지 않는다.
        assertThatThrownBy(() -> close(cohortId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION));

        verify(eventPublisher, never()).publishCohortClosed(any());
        assertThat(membershipRows(cohortId, "ACTIVE")).isEqualTo(1);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void activate(Long cohortId) {
        fixture.createLab(
                cohortId,
                "트리거-활성화-실습실-" + cohortId,
                1
        );
        cohortService.changeStatus(
                cohortId, new ChangeCohortStatusCommand(CohortStatus.ACTIVE), GlobalRole.SYSTEM_ADMIN);
    }

    private CohortResponse close(Long cohortId) {
        return cohortService.changeStatus(
                cohortId, new ChangeCohortStatusCommand(CohortStatus.CLOSED), GlobalRole.SYSTEM_ADMIN);
    }

    private void endMembership(Long membershipId) {
        jdbcTemplate.update("""
                UPDATE learning_service.cohort_memberships
                   SET status = 'ENDED', ended_at = now() WHERE id = ?
                """, membershipId);
    }

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

    private String cohortStatus(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.cohorts WHERE id = ?
                """, String.class, cohortId);
    }

    private int membershipRows(Long cohortId, String status) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.cohort_memberships
                 WHERE cohort_id = ? AND status = ?
                """, Integer.class, cohortId, status);
        return count == null ? 0 : count;
    }

    private Object teamDeletedAt(Long teamId) {
        return jdbcTemplate.queryForObject("""
                SELECT deleted_at FROM learning_service.teams WHERE id = ?
                """, Object.class, teamId);
    }

    private String occupancyStatus(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies WHERE id = ?
                """, String.class, occupancyId);
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

    private String attendanceStatus(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT auto_status
                  FROM learning_service.attendance_records
                 WHERE cohort_membership_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """, String.class, membershipId);
    }

    private int openPresenceRows(Long cohortId) {
        return count("""
                SELECT count(*)
                  FROM learning_service.presence_intervals presence
                  JOIN learning_service.attendance_records attendance
                    ON attendance.id = presence.attendance_id
                  JOIN learning_service.cohort_memberships membership
                    ON membership.id = attendance.cohort_membership_id
                 WHERE membership.cohort_id = ?
                   AND presence.ended_at IS NULL
                """, cohortId);
    }

    private OffsetDateTime latestPresenceEndedAt(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT presence.ended_at
                  FROM learning_service.presence_intervals presence
                  JOIN learning_service.attendance_records attendance
                    ON attendance.id = presence.attendance_id
                 WHERE attendance.cohort_membership_id = ?
                 ORDER BY presence.id DESC
                 LIMIT 1
                """, OffsetDateTime.class, membershipId);
    }

    private OffsetDateTime membershipEndedAt(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at
                  FROM learning_service.cohort_memberships
                 WHERE id = ?
                """, OffsetDateTime.class, membershipId);
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
