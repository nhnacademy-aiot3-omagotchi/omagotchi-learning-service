package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceIntervalRepository extends JpaRepository<PresenceInterval, Long> {

    Optional<PresenceInterval> findFirstByAttendanceIdAndEndedAtIsNullOrderByStartedAtDesc(Long attendanceId);

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
                           r.cohortMembershipId, i.startedAt)
                  FROM PresenceInterval i
                  JOIN AttendanceRecord r ON r.id = i.attendanceId
                  JOIN CohortMembership m ON m.id = r.cohortMembershipId
                 WHERE m.userId = :userId
                   AND i.endedAt IS NULL
                   AND i.state <> site.omagotchi.learningservice.attendance.domain.PresenceState.AWAY
                 ORDER BY i.startedAt DESC""")
    List<OpenPresenceView> findOpenPresences(@Param("userId") UUID userId);
}
