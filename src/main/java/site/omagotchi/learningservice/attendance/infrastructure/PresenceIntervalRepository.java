package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;

import java.time.Instant;
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

    Optional<PresenceInterval> findFirstByAttendanceIdAndEndedAtIsNullOrderByStartedAtDesc(Long attendanceId);

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
}
