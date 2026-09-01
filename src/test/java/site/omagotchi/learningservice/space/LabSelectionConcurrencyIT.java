package site.omagotchi.learningservice.space;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
@DisplayName("실습실 선택 동시성")
class LabSelectionConcurrencyIT {

    @Autowired
    private OccupancyTestFixture fixture;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private SpaceCommandService spaceCommandService;

    @Autowired
    private RoomOccupancyService roomOccupancyService;

    @Autowired
    private CohortAttendancePolicyRepository attendancePolicyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private VacancyAlertSender vacancyAlertSender;

    @Test
    @DisplayName("정원이 한 자리 남은 실습실을 동시에 선택하면 한 명만 이동한다")
    void onlyOneMemberMovesIntoTheLastLabSeat() throws Exception {
        Long cohortId = fixture.createCohort("마지막-좌석-동시선택");
        OccupancyTestFixture.Member first = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member second = fixture.createActiveMember(cohortId);
        Long targetLabId = fixture.createLab(cohortId, "마지막-좌석-LAB", 1);

        List<Attempt> attempts = runConcurrently(
                () -> attendanceService.moveLab(cohortId, first.userId(), targetLabId),
                () -> attendanceService.moveLab(cohortId, second.userId(), targetLabId)
        );

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(Attempt::errorCode)
                .isEqualTo(SpaceErrorCode.LAB_CAPACITY_EXCEEDED);
        assertThat(openPresenceRows(targetLabId)).isEqualTo(1);
    }

    @Test
    @DisplayName("실습실 선택과 비활성화가 경합해도 비활성 공간에는 열린 체류가 남지 않는다")
    void selectionAndDeactivationRemainConsistent() throws Exception {
        Long cohortId = fixture.createCohort("선택-비활성화-경합");
        OccupancyTestFixture.Member manager = fixture.createActiveMember(cohortId);
        Long targetLabId = fixture.createLab(cohortId, "선택-비활성화-LAB", 10);

        List<Attempt> attempts = runConcurrently(
                () -> attendanceService.moveLab(cohortId, manager.userId(), targetLabId),
                () -> spaceCommandService.deactivate(
                        targetLabId,
                        "동시성 검증",
                        manager.userId()
                )
        );

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(Attempt::errorCode)
                .isIn(
                        SpaceErrorCode.SPACE_HAS_CURRENT_PRESENCE,
                        SpaceErrorCode.LAB_NOT_SELECTABLE
                );

        String status = spaceStatus(targetLabId);
        int openPresenceRows = openPresenceRows(targetLabId);
        if (status.equals("ACTIVE")) {
            assertThat(openPresenceRows).isEqualTo(1);
        } else {
            assertThat(status).isEqualTo("INACTIVE");
            assertThat(openPresenceRows).isZero();
        }
    }

    @Test
    @DisplayName("회의 입실과 체크아웃이 경합해도 활성 회의와 체크아웃이 함께 남지 않는다")
    void meetingEntryAndCheckoutRemainAtomic() throws Exception {
        Long cohortId = fixture.createCohort("회의입실-체크아웃-경합");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long meetingRoomId = fixture.createMeetingRoom(cohortId, "회의입실-체크아웃-회의실", 10);
        attendancePolicyRepository.save(CohortAttendancePolicy.create(
                cohortId,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(23, 59),
                LocalTime.of(23, 59),
                30,
                member.userId()
        ));

        List<Attempt> attempts = runConcurrently(
                () -> roomOccupancyService.start(meetingRoomId, member.userId()),
                () -> attendanceService.checkOut(cohortId, member.userId())
        );

        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(Attempt::errorCode)
                .isIn(
                        AttendanceErrorCode.ATTENDANCE_ACTIVE_MEETING_EXISTS,
                        AttendanceErrorCode.PRESENCE_TRANSITION_NOT_ALLOWED
                );

        int activeOccupancyRows = activeOccupancyRows(meetingRoomId);
        int checkedOutRows = checkedOutRows(member.membershipId());
        assertThat(activeOccupancyRows + checkedOutRows).isEqualTo(1);
        if (activeOccupancyRows == 1) {
            assertThat(openMeetingPresenceRows(member.membershipId(), meetingRoomId)).isEqualTo(1);
            assertThat(checkedOutRows).isZero();
        } else {
            assertThat(checkedOutRows).isEqualTo(1);
            assertThat(openPresenceRowsForMembership(member.membershipId())).isZero();
        }
    }

    private List<Attempt> runConcurrently(Action firstAction, Action secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(() -> run(firstAction, ready, start));
            Future<Attempt> second = executor.submit(() -> run(secondAction, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt run(
            Action action,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 시작 신호를 받지 못했습니다.");
        }
        try {
            action.run();
            return new Attempt(true, null);
        } catch (BusinessException exception) {
            return new Attempt(false, exception.getErrorCode());
        }
    }

    private int openPresenceRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.presence_intervals
                 WHERE space_id = ?
                   AND ended_at IS NULL
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private String spaceStatus(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                  FROM learning_service.spaces
                 WHERE id = ?
                """, String.class, spaceId);
    }

    private int activeOccupancyRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.room_occupancies
                 WHERE space_id = ?
                   AND status = 'ACTIVE'
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private int checkedOutRows(Long membershipId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.attendance_records
                 WHERE cohort_membership_id = ?
                   AND checked_out_at IS NOT NULL
                """, Integer.class, membershipId);
        return count == null ? 0 : count;
    }

    private int openMeetingPresenceRows(Long membershipId, Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.presence_intervals presence
                  JOIN learning_service.attendance_records attendance
                    ON attendance.id = presence.attendance_id
                 WHERE attendance.cohort_membership_id = ?
                   AND presence.space_id = ?
                   AND presence.state = 'MEETING'
                   AND presence.ended_at IS NULL
                """, Integer.class, membershipId, spaceId);
        return count == null ? 0 : count;
    }

    private int openPresenceRowsForMembership(Long membershipId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.presence_intervals presence
                  JOIN learning_service.attendance_records attendance
                    ON attendance.id = presence.attendance_id
                 WHERE attendance.cohort_membership_id = ?
                   AND presence.ended_at IS NULL
                """, Integer.class, membershipId);
        return count == null ? 0 : count;
    }

    @FunctionalInterface
    private interface Action {
        void run();
    }

    private record Attempt(boolean succeeded, Object errorCode) {
    }
}
