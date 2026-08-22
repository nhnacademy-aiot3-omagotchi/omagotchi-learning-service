package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
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
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 기수 매니저의 점유 강제 종료 (MR-21).
 *
 * <p><b>실제 PostgreSQL과 영속성 Context가 있어야 의미가 있다.</b> 이 흐름은 한 Transaction
 * 안에서 <b>Entity 변경(점유 상태)과 벌크 DML(참여자 마감·대기 신청 삭제)을 섞는다.</b>
 * Mock 위에서는 셋 다 "호출됐다"로 통과하지만, 실제로는 벌크 DML의 {@code clearAutomatically}
 * 하나가 아직 flush되지 않은 Entity 변경을 통째로 버릴 수 있다 — 그러면 신청만 지워지고
 * 점유는 ACTIVE로 남는다. 그 회귀는 여기서만 드러난다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyForceReleaseIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @Autowired
    VacancyAlertService vacancyAlertService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    /**
     * 세 가지가 <b>같은 Transaction에서 함께</b> 반영돼야 한다. 하나라도 빠지면 나머지가
     * 성공한 것이 오히려 해롭다 — 신청만 지워지고 점유가 남으면 그 방은 아무도 쓸 수 없다.
     */
    @Test
    @DisplayName("강제 종료는 점유 상태·참여자 마감·대기 신청 삭제를 함께 반영한다.")
    void forceReleaseAppliesStatusParticipantsAndAlertsTogether() {
        Long cohortId = fixture.createCohort("강제종료-기본");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-기본-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        vacancyAlertService.request(roomId, null, waiter.userId());

        roomOccupancyLifecycleService.forceRelease(roomId, manager.userId());

        assertThat(occupancyStatus(occupancyId)).isEqualTo("FORCE_RELEASED");
        assertThat(occupancyEndedAt(occupancyId)).isNotNull();
        assertThat(openParticipantRows(occupancyId)).isZero();
        assertThat(waitingAlertRows(roomId)).isZero();
    }

    /** 공간 회수가 목적이라 대기자에게 알리면 안 된다 (MR-21). */
    @Test
    @DisplayName("강제 종료는 공실 알림을 발송하지 않는다.")
    void forceReleaseSendsNoVacancyAlert() {
        Long cohortId = fixture.createCohort("강제종료-미발송");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-미발송-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        roomOccupancyLifecycleService.forceRelease(roomId, manager.userId());

        verify(vacancyAlertSender, never()).sendVacancyAlert(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 강제 종료가 실제로 방을 회수했는지는 "다시 점유되는가"로만 확인된다. 상태만 바뀌고
     * 참여자가 열려 있으면 점유자는 {@code uq_occupancy_participants_one_active}에 묶여
     * 다른 방도 잡지 못한다.
     */
    @Test
    @DisplayName("강제 종료한 방은 곧바로 다시 점유할 수 있다.")
    void roomIsImmediatelyOccupiableAfterForceRelease() {
        Long cohortId = fixture.createCohort("강제종료-재점유");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member next = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-재점유-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        roomOccupancyLifecycleService.forceRelease(roomId, manager.userId());

        roomOccupancyService.start(roomId, next.userId());

        assertThat(activeOccupancyRows(roomId)).isEqualTo(1);
    }

    /** 권한은 요청자의 기수가 아니라 <b>점유자의</b> 기수에서 판정한다 (명세 02 §2). */
    @Test
    @DisplayName("타 기수 매니저는 강제 종료할 수 없다.")
    void managerOfAnotherCohortCannotForceRelease() {
        Long occupierCohortId = fixture.createCohort("강제종료-타기수-점유");
        Long otherCohortId = fixture.createCohort("강제종료-타기수-매니저");
        OccupancyTestFixture.Member otherManager = fixture.createActiveMember(otherCohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(occupierCohortId);
        Long roomId = fixture.createMeetingRoom(occupierCohortId, "강제종료-타기수-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        UUID otherManagerUserId = otherManager.userId();

        assertThatThrownBy(() -> roomOccupancyLifecycleService.forceRelease(roomId, otherManagerUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.NOT_COHORT_MANAGER));

        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("매니저가 아닌 같은 기수 사람은 강제 종료할 수 없다.")
    void plainMemberCannotForceRelease() {
        Long cohortId = fixture.createCohort("강제종료-비매니저");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member member = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-비매니저-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        UUID memberUserId = member.userId();

        assertThatThrownBy(() -> roomOccupancyLifecycleService.forceRelease(roomId, memberUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.NOT_COHORT_MANAGER));

        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
    }

    /** 이미 발송된 신청은 이력이다. 지우면 "알림을 보낸 적 있다"는 사실이 사라진다. */
    @Test
    @DisplayName("이미 발송된 신청은 강제 종료로 지워지지 않는다.")
    void keepsAlreadyNotifiedAlerts() {
        Long cohortId = fixture.createCohort("강제종료-이력보존");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-이력보존-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());
        markAllNotified(roomId);

        roomOccupancyLifecycleService.forceRelease(roomId, manager.userId());

        assertThat(allAlertRows(roomId)).isEqualTo(1);
        assertThat(waitingAlertRows(roomId)).isZero();
    }

    @Test
    @DisplayName("활성 점유가 없으면 이미 종료된 점유로 처리한다.")
    void treatsMissingActiveOccupancyAsEnded() {
        Long cohortId = fixture.createCohort("강제종료-빈방");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "강제종료-빈방-1", 8);
        UUID managerUserId = manager.userId();

        assertThatThrownBy(() -> roomOccupancyLifecycleService.forceRelease(roomId, managerUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(OccupancyErrorCode.OCCUPANCY_ENDED));
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

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

    private int activeOccupancyRows(Long spaceId) {
        return count("""
                SELECT count(*) FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, spaceId);
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

    private int allAlertRows(Long spaceId) {
        return count("""
                SELECT count(*) FROM learning_service.vacancy_alerts WHERE space_id = ?
                """, spaceId);
    }

    private int count(String sql, Long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
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
