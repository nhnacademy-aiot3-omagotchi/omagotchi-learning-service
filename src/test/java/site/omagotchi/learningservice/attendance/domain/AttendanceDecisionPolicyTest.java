package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("출석 판정 정책")
class AttendanceDecisionPolicyTest {

    private static final UUID UPDATED_BY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("입실 없음은 결석")
    void absentWithoutCheckIn() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                null,
                time("18:00"),
                List.of()
        );

        assertEquals(AttendanceStatus.ABSENT, decision.status());
    }

    @Test
    @DisplayName("09 이전 입실 + 퇴실 없음은 결석")
    void absentWithoutCheckOutAfterEarlyCheckIn() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                null,
                List.of()
        );

        assertEquals(AttendanceStatus.ABSENT, decision.status());
    }

    @Test
    @DisplayName("09 이전 입실 + 18 이후 퇴실은 출석")
    void presentWhenEarlyCheckInAndLateCheckOut() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("18:10"),
                List.of()
        );

        assertEquals(AttendanceStatus.PRESENT, decision.status());
    }

    @Test
    @DisplayName("09 이전 입실 + 18 이전 퇴실은 조퇴")
    void leftEarlyWhenEarlyCheckOut() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("17:50"),
                List.of()
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.LEFT_EARLY, decision.status()),
                () -> assertEquals(10, decision.earlyLeaveMinutes())
        );
    }

    @Test
    @DisplayName("09 이후 입실 + 18 이후 퇴실은 지각")
    void lateWhenLateCheckInAndLateCheckOut() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("09:10"),
                time("18:10"),
                List.of()
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.LATE, decision.status()),
                () -> assertEquals(10, decision.lateMinutes())
        );
    }

    @Test
    @DisplayName("1초 지각은 1분 지각")
    void lateOneMinuteWhenCheckInOneSecondAfterStart() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decideCheckIn(
                policy(60),
                time("09:00:01")
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.LATE, decision.status()),
                () -> assertEquals(1, decision.lateMinutes())
        );
    }

    @Test
    @DisplayName("1초 조퇴는 1분 조퇴")
    void leftEarlyOneMinuteWhenCheckOutOneSecondBeforeEnd() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("17:59:59"),
                List.of()
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.LEFT_EARLY, decision.status()),
                () -> assertEquals(1, decision.earlyLeaveMinutes())
        );
    }

    @Test
    @DisplayName("부재 후 18 이전 제한시간 내 복귀하고 18 이후 퇴실하면 출석")
    void presentWhenAwayReturnsBeforeEndWithinAllowedMinutes() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("18:10"),
                List.of(away("13:00", "13:30"))
        );

        assertEquals(AttendanceStatus.PRESENT, decision.status());
    }

    @Test
    @DisplayName("부재 복귀가 18 이후면 18 이후 퇴실해도 조퇴")
    void leftEarlyWhenAwayReturnsAfterEnd() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("18:10"),
                List.of(away("17:30", "18:01"))
        );

        assertEquals(AttendanceStatus.LEFT_EARLY, decision.status());
    }

    @Test
    @DisplayName("부재 시간이 제한시간을 넘으면 18 이후 퇴실해도 조퇴")
    void leftEarlyWhenAwayExceedsAllowedMinutes() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(30),
                time("08:50"),
                time("18:10"),
                List.of(away("13:00", "13:31"))
        );

        assertEquals(AttendanceStatus.LEFT_EARLY, decision.status());
    }

    @Test
    @DisplayName("부재가 무효이고 퇴실이 없으면 결석")
    void absentWhenInvalidAwayAndNoCheckOut() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                null,
                List.of(away("17:30", "18:01"))
        );

        assertEquals(AttendanceStatus.ABSENT, decision.status());
    }

    @Test
    @DisplayName("09 이후 입실 + 무효 부재 + 18 이후 퇴실은 지각+조퇴")
    void lateAndLeftEarlyWhenLateCheckInAndInvalidAway() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("09:10"),
                time("18:10"),
                List.of(away("17:30", "18:01"))
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.LATE_LEFT_EARLY, decision.status()),
                () -> assertEquals(10, decision.lateMinutes()),
                () -> assertEquals(1, decision.earlyLeaveMinutes())
        );
    }

    @Test
    @DisplayName("실습실과 회의실 위치 구간은 출석 판정 결과를 바꾸지 않는다")
    void ignoresPresentAndMeetingLocationsWhenDecidingAttendance() {
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy(60),
                time("08:50"),
                time("18:10"),
                List.of(
                        interval(PresenceState.PRESENT, 10L, "08:50", "12:00"),
                        interval(PresenceState.MEETING, 20L, "12:00", "13:00"),
                        interval(PresenceState.PRESENT, 30L, "13:00", "18:10")
                )
        );

        assertAll(
                () -> assertEquals(AttendanceStatus.PRESENT, decision.status()),
                () -> assertEquals(0, decision.lateMinutes()),
                () -> assertEquals(0, decision.earlyLeaveMinutes())
        );
    }

    private CohortAttendancePolicy policy(int allowedAwayMinutes) {
        return CohortAttendancePolicy.create(
                1L,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                allowedAwayMinutes,
                UPDATED_BY
        );
    }

    private PresenceInterval away(String startedAt, String endedAt) {
        return interval(PresenceState.AWAY, null, startedAt, endedAt);
    }

    private PresenceInterval interval(
            PresenceState state,
            Long spaceId,
            String startedAt,
            String endedAt
    ) {
        PresenceInterval interval = PresenceInterval.start(
                1L,
                state,
                spaceId,
                time(startedAt)
        );
        interval.end(time(endedAt));
        return interval;
    }

    private Instant time(String value) {
        String localTime = value.length() == 5 ? value + ":00" : value;
        return Instant.parse("2026-07-29T%s+09:00".formatted(localTime));
    }
}
