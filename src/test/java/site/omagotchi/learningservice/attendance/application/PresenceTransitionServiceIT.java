package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("체류 구간 전환 PostgreSQL 통합")
class PresenceTransitionServiceIT {

    private static final UUID ADMIN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000301"
    );
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 8, 31);
    private static final Instant STARTED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private PresenceTransitionService service;

    @Autowired
    private AttendancePresenceQueryService presenceQueryService;

    @Autowired
    private AttendancePresenceQuery attendancePresenceQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("현재 위치 조회는 체크인된 출결의 최신 열린 공간 구간을 반환한다")
    void findsLatestOpenSpacePresence() {
        Scenario scenario = saveScenario(false);

        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT
        );

        assertThat(attendancePresenceQuery.findCurrentPresences(
                scenario.membershipId(),
                ATTENDANCE_DATE
        ))
                .singleElement()
                .satisfies(presence -> {
                    assertThat(presence.spaceId()).isEqualTo(scenario.firstLabId());
                    assertThat(presence.state().name()).isEqualTo("PRESENT");
                    assertThat(presence.startedAt()).isEqualTo(STARTED_AT);
                });
    }

    @Test
    @DisplayName("현재 위치 조회는 더 최신 PRESENT보다 이전 집계일의 열린 MEETING을 앞에 둔다")
    void ordersOpenMeetingBeforeNewerPresence() {
        Scenario scenario = saveScenario(false);
        LocalDate nextDate = ATTENDANCE_DATE.plusDays(1);
        Instant enteredMeetingAt = Instant.parse("2026-08-31T02:00:00Z");
        Instant nextDaySelectedAt = Instant.parse("2026-09-01T00:00:00Z");

        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT
        );
        service.enterMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                enteredMeetingAt
        );
        Long nextAttendanceId = saveAttendance(
                scenario.membershipId(),
                nextDate,
                nextDaySelectedAt
        );
        service.moveLab(
                nextAttendanceId,
                scenario.membershipId(),
                scenario.secondLabId(),
                nextDaySelectedAt
        );

        List<CurrentPresenceResult> presences = attendancePresenceQuery.findCurrentPresences(
                scenario.membershipId(),
                nextDate
        );

        // startedAt 만 보면 오늘 PRESENT(09-01)가 앞서지만, 회의 우선 정렬이 이를 뒤집는다.
        assertThat(presences)
                .extracting(presence -> presence.state().name(), CurrentPresenceResult::spaceId)
                .containsExactly(
                        tuple("MEETING", scenario.meetingId()),
                        tuple("PRESENT", scenario.secondLabId())
                );
        // CurrentPresenceQueryService 가 findFirst() 로 고르는 값이 회의여야 한다.
        assertThat(presences.getFirst().startedAt()).isEqualTo(enteredMeetingAt);
    }

    @Test
    @DisplayName("현재 위치 조회는 열린 회의가 여럿이면 가장 늦게 시작한 회의를 앞에 둔다")
    void ordersMultipleOpenMeetingsByStartedAtDesc() {
        Scenario scenario = saveScenario(false);
        LocalDate nextDate = ATTENDANCE_DATE.plusDays(1);
        Instant olderMeetingAt = Instant.parse("2026-08-31T02:00:00Z");
        Instant newerMeetingAt = Instant.parse("2026-09-01T02:00:00Z");

        savePresence(
                scenario.attendanceId(),
                "MEETING",
                scenario.meetingId(),
                olderMeetingAt,
                null
        );
        Long nextAttendanceId = saveAttendance(
                scenario.membershipId(),
                nextDate,
                Instant.parse("2026-09-01T00:00:00Z")
        );
        Long otherMeetingId = saveSpace(saveCohort(), "MEETING");
        savePresence(nextAttendanceId, "MEETING", otherMeetingId, newerMeetingAt, null);

        List<CurrentPresenceResult> presences = attendancePresenceQuery.findCurrentPresences(
                scenario.membershipId(),
                nextDate
        );

        // 같은 상태끼리는 startedAt DESC 가 순서를 정한다.
        assertThat(presences)
                .extracting(CurrentPresenceResult::spaceId)
                .containsExactly(otherMeetingId, scenario.meetingId());
    }

    @Test
    @DisplayName("집계일이 바뀌어 더 최신 PRESENT가 생겨도 이탈은 이전 출결의 열린 MEETING을 선택한다")
    void selectsPreviousAttendanceMeetingAcrossAggregationDateBoundary() {
        Scenario scenario = saveScenario(false);
        Instant enteredMeetingAt = Instant.parse("2026-08-31T02:00:00Z");
        Instant nextDaySelectedAt = Instant.parse("2026-09-01T00:00:00Z");

        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT
        );
        service.enterMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                enteredMeetingAt
        );
        Long nextAttendanceId = saveAttendance(
                scenario.membershipId(),
                ATTENDANCE_DATE.plusDays(1),
                nextDaySelectedAt
        );
        service.moveLab(
                nextAttendanceId,
                scenario.membershipId(),
                scenario.secondLabId(),
                nextDaySelectedAt
        );

        var meeting = presenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(scenario.membershipId()),
                scenario.meetingId()
        ).get(scenario.membershipId());

        assertThat(meeting.attendanceId()).isEqualTo(scenario.attendanceId());
        assertThat(openIntervalCount(nextAttendanceId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("실습실 이동과 회의 입퇴실은 시각이 맞닿은 행을 남기고 반복 요청은 행을 늘리지 않는다")
    void preservesAdjacentHistoryAcrossTransitions() {
        Scenario scenario = saveScenario(false);
        Instant movedAt = Instant.parse("2026-08-31T01:00:00Z");
        Instant enteredMeetingAt = Instant.parse("2026-08-31T02:00:00Z");
        Instant leftMeetingAt = Instant.parse("2026-08-31T03:00:00Z");
        Instant closedAt = Instant.parse("2026-08-31T04:00:00Z");

        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT
        );
        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT.plusSeconds(30)
        );
        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.secondLabId(),
                movedAt
        );
        service.enterMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                enteredMeetingAt
        );
        service.enterMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                enteredMeetingAt.plusSeconds(30)
        );
        service.leaveMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                leftMeetingAt
        );
        service.leaveMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                leftMeetingAt.plusSeconds(30)
        );
        service.closeAttendance(scenario.attendanceId(), closedAt);
        service.closeAttendance(scenario.attendanceId(), closedAt.plusSeconds(30));

        List<PresenceRow> rows = findPresenceRows(scenario.attendanceId());

        assertThat(rows).hasSize(4);
        assertRow(rows.get(0), "PRESENT", scenario.firstLabId(), STARTED_AT, movedAt);
        assertRow(rows.get(1), "PRESENT", scenario.secondLabId(), movedAt, enteredMeetingAt);
        assertRow(rows.get(2), "MEETING", scenario.meetingId(), enteredMeetingAt, leftMeetingAt);
        assertRow(rows.get(3), "PRESENT", scenario.secondLabId(), leftMeetingAt, closedAt);
        assertThat(openIntervalCount(scenario.attendanceId())).isZero();
    }

    @Test
    @DisplayName("열린 구간이 두 개면 어느 행도 보정하거나 삭제하지 않고 정합성 오류를 반환한다")
    void rejectsDuplicateOpenIntervalsWithoutChangingRows() {
        Scenario scenario = saveScenario(false);
        savePresence(
                scenario.attendanceId(),
                "PRESENT",
                scenario.firstLabId(),
                STARTED_AT,
                null
        );
        savePresence(
                scenario.attendanceId(),
                "PRESENT",
                scenario.secondLabId(),
                STARTED_AT.plusSeconds(60),
                null
        );

        assertThatThrownBy(() -> service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.secondLabId(),
                STARTED_AT.plusSeconds(120)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(AttendanceErrorCode.PRESENCE_INTERVAL_INCONSISTENT));

        assertThat(findPresenceRows(scenario.attendanceId())).hasSize(2);
        assertThat(openIntervalCount(scenario.attendanceId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("체크아웃된 출결의 회의 입실은 거절하고 기존 열린 행을 그대로 둔다")
    void rejectsMeetingEntryAfterCheckoutWithoutChangingHistory() {
        Scenario scenario = saveScenario(true);
        savePresence(
                scenario.attendanceId(),
                "PRESENT",
                scenario.firstLabId(),
                STARTED_AT,
                null
        );

        assertThatThrownBy(() -> service.enterMeeting(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.meetingId(),
                STARTED_AT.plusSeconds(60)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(AttendanceErrorCode.PRESENCE_TRANSITION_NOT_ALLOWED));

        List<PresenceRow> rows = findPresenceRows(scenario.attendanceId());
        assertThat(rows).hasSize(1);
        assertRow(
                rows.getFirst(),
                "PRESENT",
                scenario.firstLabId(),
                STARTED_AT,
                null
        );
    }

    @Test
    @DisplayName("같은 출결의 동시 실습실 이동은 출결 행 잠금으로 직렬화되어 열린 구간이 하나만 남는다")
    void serializesConcurrentLabMovesByAttendanceLock() throws Exception {
        Scenario scenario = saveScenario(false);
        service.moveLab(
                scenario.attendanceId(),
                scenario.membershipId(),
                scenario.firstLabId(),
                STARTED_AT
        );
        Instant movedAt = STARTED_AT.plusSeconds(60);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> moveAfterSignal(
                    scenario,
                    scenario.secondLabId(),
                    movedAt,
                    ready,
                    start
            ));
            Future<?> second = executor.submit(() -> moveAfterSignal(
                    scenario,
                    scenario.thirdLabId(),
                    movedAt,
                    ready,
                    start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            List<PresenceRow> rows = findPresenceRows(scenario.attendanceId());
            assertThat(rows).hasSize(3);
            assertThat(openIntervalCount(scenario.attendanceId())).isEqualTo(1L);
            assertThat(rows.getLast().spaceId())
                    .isIn(scenario.secondLabId(), scenario.thirdLabId());
        } finally {
            executor.shutdownNow();
        }
    }

    private void moveAfterSignal(
            Scenario scenario,
            Long nextLabId,
            Instant at,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 시작 신호를 받지 못했습니다.");
            }
            service.moveLab(
                    scenario.attendanceId(),
                    scenario.membershipId(),
                    nextLabId,
                    at
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Scenario saveScenario(boolean checkedOut) {
        Long cohortId = saveCohort();
        Long membershipId = saveMembership(cohortId);
        Long firstLabId = saveSpace(cohortId, "LAB");
        Long secondLabId = saveSpace(cohortId, "LAB");
        Long thirdLabId = saveSpace(cohortId, "LAB");
        Long meetingId = saveSpace(cohortId, "MEETING");
        Long attendanceId = saveAttendance(membershipId, checkedOut);
        return new Scenario(
                membershipId,
                attendanceId,
                firstLabId,
                secondLabId,
                thirdLabId,
                meetingId
        );
    }

    private Long saveCohort() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) VALUES (?, '설명', '2026-08-01', '2026-12-31', 'ACTIVE', ?)
                        RETURNING id
                        """,
                Long.class,
                "체류전환-" + UUID.randomUUID(),
                ADMIN_ID
        );
    }

    private Long saveMembership(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.cohort_memberships (
                            cohort_id, user_id, role, status,
                            requested_at, processed_at, processed_by_user_id
                        ) VALUES (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                cohortId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                ADMIN_ID
        );
    }

    private Long saveSpace(Long cohortId, String type) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.spaces (
                            cohort_id, name, space_type, capacity, status
                        ) VALUES (?, ?, ?, 20, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                cohortId,
                "체류전환-공간-" + UUID.randomUUID(),
                type
        );
    }

    private Long saveAttendance(Long membershipId, boolean checkedOut) {
        OffsetDateTime checkedOutAt = checkedOut
                ? STARTED_AT.plusSeconds(30).atOffset(ZoneOffset.UTC)
                : null;
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.attendance_records (
                            cohort_membership_id, attendance_date,
                            checked_in_at, checked_out_at,
                            auto_status, final_status
                        ) VALUES (?, ?, ?, ?, 'PRESENT', 'PRESENT')
                        RETURNING id
                        """,
                Long.class,
                membershipId,
                ATTENDANCE_DATE,
                STARTED_AT.atOffset(ZoneOffset.UTC),
                checkedOutAt
        );
    }

    private Long saveAttendance(Long membershipId, LocalDate date, Instant checkedInAt) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.attendance_records (
                            cohort_membership_id, attendance_date,
                            checked_in_at, auto_status, final_status
                        ) VALUES (?, ?, ?, 'PRESENT', 'PRESENT')
                        RETURNING id
                        """,
                Long.class,
                membershipId,
                date,
                checkedInAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void savePresence(
            Long attendanceId,
            String state,
            Long spaceId,
            Instant startedAt,
            Instant endedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO learning_service.presence_intervals (
                            attendance_id, state, space_id, started_at, ended_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                attendanceId,
                state,
                spaceId,
                startedAt.atOffset(ZoneOffset.UTC),
                endedAt == null ? null : endedAt.atOffset(ZoneOffset.UTC)
        );
    }

    private List<PresenceRow> findPresenceRows(Long attendanceId) {
        return jdbcTemplate.query("""
                        SELECT state, space_id, started_at, ended_at
                          FROM learning_service.presence_intervals
                         WHERE attendance_id = ?
                         ORDER BY started_at, id
                        """,
                (resultSet, rowNumber) -> new PresenceRow(
                        resultSet.getString("state"),
                        resultSet.getLong("space_id"),
                        resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("ended_at", OffsetDateTime.class) == null
                                ? null
                                : resultSet.getObject(
                                        "ended_at",
                                        OffsetDateTime.class
                                ).toInstant()
                ),
                attendanceId
        );
    }

    private long openIntervalCount(Long attendanceId) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                          FROM learning_service.presence_intervals
                         WHERE attendance_id = ?
                           AND ended_at IS NULL
                        """,
                Long.class,
                attendanceId
        );
        return count == null ? 0L : count;
    }

    private void assertRow(
            PresenceRow row,
            String state,
            Long spaceId,
            Instant startedAt,
            Instant endedAt
    ) {
        assertThat(row.state()).isEqualTo(state);
        assertThat(row.spaceId()).isEqualTo(spaceId);
        assertThat(row.startedAt()).isEqualTo(startedAt);
        assertThat(row.endedAt()).isEqualTo(endedAt);
    }

    private record Scenario(
            Long membershipId,
            Long attendanceId,
            Long firstLabId,
            Long secondLabId,
            Long thirdLabId,
            Long meetingId
    ) {
    }

    private record PresenceRow(
            String state,
            Long spaceId,
            Instant startedAt,
            Instant endedAt
    ) {
    }
}
