package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.attendance.application.result.PresenceIntervalView;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 체류 구간 저장소.
 *
 * <p>체류 이력은 업무 코드에서 삭제하지 않는다. 전환과 체크아웃은 기존 행의
 * {@code ended_at}을 채우고 다음 구간을 새 행으로 보존한다.</p>
 */
public interface PresenceIntervalRepository extends JpaRepository<PresenceInterval, Long> {

    /** 체크아웃되지 않은 출결에 속한 열린 체류구간을 최신순으로 조회한다. */
    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult(
                           i.spaceId, i.state, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                 WHERE r.cohortMembershipId = :cohortMembershipId
                   AND r.checkedInAt IS NOT NULL
                   AND r.checkedOutAt IS NULL
                   AND i.endedAt IS NULL
                   AND (r.attendanceDate = :attendanceDate
                        OR i.state = site.omagotchi.learningservice.attendance.domain.PresenceState.MEETING)
                 ORDER BY CASE
                              WHEN i.state = site.omagotchi.learningservice.attendance.domain.PresenceState.MEETING
                              THEN 0 ELSE 1
                          END,
                          i.startedAt DESC,
                          i.id DESC""")
    List<CurrentPresenceResult> findCurrentPresences(
            @Param("cohortMembershipId") Long cohortMembershipId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /** 열린 구간 중복을 감지할 수 있도록 한 건으로 축약하지 않는다. */
    List<PresenceInterval> findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(
            Long attendanceId
    );

    /** 현재 회의 시작 시각과 맞닿은 가장 최근 비회의 구간을 조회한다. */
    Optional<PresenceInterval>
    findFirstByAttendanceIdAndStateNotAndEndedAtOrderByStartedAtDescIdDesc(
            Long attendanceId,
            PresenceState excludedState,
            Instant endedAt
    );

    /** 성공한 회의 이탈 명령의 멱등 재요청인지 판정할 최근 회의 구간을 조회한다. */
    Optional<PresenceInterval>
    findFirstByAttendanceIdAndStateAndEndedAtOrderByStartedAtDescIdDesc(
            Long attendanceId,
            PresenceState state,
            Instant endedAt
    );

    List<PresenceInterval> findByAttendanceIdOrderByStartedAtAsc(Long attendanceId);

    /**
     * 계정의 열린 재실 구간을 최신순으로 조회한다.
     *
     * <p>{@code presence_intervals}는 {@code attendance_id}만 갖고 있어 계정으로 바로
     * 찾을 수 없다. 기수 소속까지 두 번 조인해야 {@code userId} → 재실 경로가 완성된다:
     * {@code presence_intervals → attendance_records → cohort_memberships}.</p>
     *
     * <p>{@code AWAY}를 제외하는 것이 재실 판정이다. {@code MEETING}·{@code STUDYING}은
     * 모두 건물 안에 있는 상태이므로 재실로 본다 — 자리를 비운 것은 {@code AWAY} 하나뿐이다.
     * (CT-01 협의 항목: 이 범위가 확정되면 여기 조건을 조정한다.)</p>
     *
     * <p>단건이 아니라 목록인 것은 열린 구간이 여럿일 수 있기 때문이다. 다기수 담당자는
     * 기수마다 {@code attendance_records}가 따로 생겨 두 기수에 동시에 출근 처리될 수 있다.
     * 무엇을 고를지는 호출부가 아니라 {@code AttendancePresenceQueryService}가 정한다.</p>
     */
    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.OpenPresenceView(
                           r.id, r.cohortMembershipId, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                  JOIN CohortMembership m ON m.id = r.cohortMembershipId
                 WHERE m.userId = :userId
                   AND i.endedAt IS NULL
                   AND i.state <> site.omagotchi.learningservice.attendance.domain.PresenceState.AWAY
                 ORDER BY i.startedAt DESC, i.id DESC""")
    List<OpenPresenceView> findOpenPresences(@Param("userId") UUID userId);

    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView(
                           m.userId, r.id, r.cohortMembershipId, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                  JOIN CohortMembership m ON m.id = r.cohortMembershipId
                 WHERE m.userId IN :userIds
                   AND i.endedAt IS NULL
                   AND i.state <> site.omagotchi.learningservice.attendance.domain.PresenceState.AWAY
                 ORDER BY m.userId ASC, i.startedAt DESC, i.id DESC""")
    List<OpenUserPresenceView> findOpenPresences(@Param("userIds") Collection<UUID> userIds);

    /**
     * 소속의 체류 구간을 기간으로 조회한다.
     *
     * <p>열린 구간만 보는 {@code findOpenPresences}와 달리 지나간 구간을 함께 돌려준다.
     * 학습 기록에 "그 시각 어느 공간이었는가"를 붙이는 데 쓴다.</p>
     *
     * <p>조회 키가 {@code userId}가 아니라 {@code cohortMembershipId}인 것은 소비처가
     * 기수 단위이기 때문이다. 학습 기록도 소속으로 쌓이므로 같은 키를 써야 다기수 담당자의
     * 다른 기수 구간이 섞이지 않는다. 그래서 {@code cohort_memberships} 조인도 필요 없다.</p>
     *
     * <p>범위와 <b>겹치기만 하면</b> 포함한다 — 구간이 범위 안에 완전히 들어올 필요는 없다.
     * 경계에 걸친 구간을 어디까지 인정할지는 받는 쪽이 정하도록 자르지 않고 그대로 돌려준다.
     * 진행 중인 구간({@code ended_at IS NULL})도 같은 이유로 그대로 포함한다.</p>
     *
     * <p>{@code AWAY} 제외가 재실 판정이다 — {@code findOpenPresences}와 같은 기준을 쓴다.</p>
     */
    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.PresenceIntervalView(
                           i.spaceId, i.state, i.startedAt, i.endedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                 WHERE r.cohortMembershipId = :cohortMembershipId
                   AND i.state <> site.omagotchi.learningservice.attendance.domain.PresenceState.AWAY
                   AND i.startedAt < :toExclusive
                   AND (i.endedAt IS NULL OR i.endedAt > :from)
                 ORDER BY i.startedAt ASC, i.id ASC""")
    List<PresenceIntervalView> findPresenceIntervals(
            @Param("cohortMembershipId") Long cohortMembershipId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    /** 점유 참여 행에 저장된 정확한 소속별 최신 열린 재실 구간을 조회한다. */
    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.OpenPresenceView(
                           r.id, r.cohortMembershipId, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                 WHERE r.cohortMembershipId IN :membershipIds
                   AND i.endedAt IS NULL
                   AND i.state <> site.omagotchi.learningservice.attendance.domain.PresenceState.AWAY
                 ORDER BY r.cohortMembershipId ASC, i.startedAt DESC, i.id DESC""")
    List<OpenPresenceView> findOpenPresencesByMembershipIds(
            @Param("membershipIds") Collection<Long> membershipIds
    );

    /**
     * 회의 이탈용 조회. 최신 PRESENT가 아니라 실제로 닫아야 할 열린 MEETING을 찾는다.
     * {@code meetingSpaceId}가 null인 경우는 소속 종료 정리처럼 점유 공간을 더 이상
     * 신뢰할 수 없는 복구 경로이며, 상태와 소속만으로 찾는다.
     */
    @Query("""
                SELECT new site.omagotchi.learningservice.attendance.application.result.OpenPresenceView(
                           r.id, r.cohortMembershipId, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                 WHERE r.cohortMembershipId IN :membershipIds
                   AND i.endedAt IS NULL
                   AND i.state = site.omagotchi.learningservice.attendance.domain.PresenceState.MEETING
                   AND (:meetingSpaceId IS NULL OR i.spaceId = :meetingSpaceId)
                 ORDER BY r.cohortMembershipId ASC, r.id ASC, i.id ASC""")
    List<OpenPresenceView> findOpenMeetingPresencesByMembershipIds(
            @Param("membershipIds") Collection<Long> membershipIds,
            @Param("meetingSpaceId") Long meetingSpaceId
    );

    /**
     * 이 소속이 지금 회의 중인가.
     *
     * <p>출결 기록이 아니라 소속 단위로 보는 것이 중요하다. 회의가 집계일 경계를 넘으면
     * 열린 {@code MEETING}은 이전 집계일 출결에 남고, 그 사이 새 출결로 체크인할 수 있다.
     * 출결 단위로 보면 그때 "한 사람이 회의실과 실습실에 동시에 있는" 이력이 생긴다.</p>
     */
    @Query("""
                SELECT (COUNT(i) > 0)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                 WHERE r.cohortMembershipId = :membershipId
                   AND i.endedAt IS NULL
                   AND i.state = site.omagotchi.learningservice.attendance.domain.PresenceState.MEETING""")
    boolean existsOpenMeetingByMembershipId(@Param("membershipId") Long membershipId);
}
