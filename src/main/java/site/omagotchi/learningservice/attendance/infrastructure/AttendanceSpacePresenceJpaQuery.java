package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceSpacePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기존 presence_intervals만으로 현재 체류와 직전 실습실 복귀 예약을 계산한다.
 */
@Repository
@RequiredArgsConstructor
public class AttendanceSpacePresenceJpaQuery implements AttendanceSpacePresenceQuery {

    private static final String CURRENT_PRESENCE_QUERY = """
            SELECT presence.space_id, COUNT(DISTINCT presence.attendance_id)
              FROM learning_service.attendance_records attendance
              JOIN learning_service.presence_intervals presence
                ON presence.attendance_id = attendance.id
             WHERE presence.space_id IN (:spaceIds)
               AND presence.ended_at IS NULL
               AND presence.state <> 'AWAY'
               AND attendance.checked_out_at IS NULL
               AND attendance.attendance_date = :attendanceDate
             GROUP BY presence.space_id
            """;

    private static final String RETURN_RESERVATION_QUERY = """
            SELECT previous.space_id, COUNT(DISTINCT meeting.attendance_id)
              FROM learning_service.attendance_records attendance
              JOIN learning_service.presence_intervals meeting
                ON meeting.attendance_id = attendance.id
              JOIN LATERAL (
                    SELECT candidate.space_id
                     FROM learning_service.presence_intervals candidate
                     WHERE candidate.attendance_id = meeting.attendance_id
                       AND candidate.ended_at = meeting.started_at
                       AND candidate.state <> 'MEETING'
                     ORDER BY candidate.started_at DESC, candidate.id DESC
                     LIMIT 1
              ) previous ON TRUE
             WHERE meeting.ended_at IS NULL
               AND meeting.state = 'MEETING'
               AND previous.space_id IN (:spaceIds)
               AND attendance.checked_out_at IS NULL
               AND attendance.attendance_date = :attendanceDate
            GROUP BY previous.space_id
            """;

    private static final String RESERVED_ATTENDANCE_QUERY = """
            SELECT EXISTS (
                SELECT 1
                  FROM learning_service.attendance_records attendance
                  JOIN learning_service.presence_intervals current_interval
                    ON current_interval.attendance_id = attendance.id
                 WHERE attendance.id = :attendanceId
                   AND attendance.checked_out_at IS NULL
                   AND attendance.attendance_date = :attendanceDate
                   AND current_interval.ended_at IS NULL
                   AND (
                        (current_interval.state <> 'AWAY' AND current_interval.space_id = :spaceId)
                        OR (
                            current_interval.state = 'MEETING'
                            AND EXISTS (
                                SELECT 1
                                  FROM learning_service.presence_intervals previous
                                 WHERE previous.attendance_id = current_interval.attendance_id
                                   AND previous.ended_at = current_interval.started_at
                                   AND previous.state <> 'MEETING'
                                   AND previous.space_id = :spaceId
                            )
                        )
                   )
            )
            """;

    private final EntityManager entityManager;

    @Override
    public Map<Long, SpacePresenceSummary> summarize(
            Collection<Long> spaceIds,
            LocalDate attendanceDate
    ) {
        Map<Long, Long> currentCounts = counts(
                CURRENT_PRESENCE_QUERY,
                spaceIds,
                attendanceDate
        );
        Map<Long, Long> returnCounts = counts(
                RETURN_RESERVATION_QUERY,
                spaceIds,
                attendanceDate
        );
        Map<Long, SpacePresenceSummary> summaries = new HashMap<>();

        for (Long spaceId : spaceIds) {
            summaries.put(spaceId, new SpacePresenceSummary(
                    currentCounts.getOrDefault(spaceId, 0L),
                    returnCounts.getOrDefault(spaceId, 0L)
            ));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public boolean isReserved(Long spaceId, Long attendanceId, LocalDate attendanceDate) {
        Object result = entityManager.createNativeQuery(
                        RESERVED_ATTENDANCE_QUERY,
                        Boolean.class
                )
                .setParameter("spaceId", spaceId)
                .setParameter("attendanceId", attendanceId)
                .setParameter("attendanceDate", attendanceDate)
                .getSingleResult();
        return (Boolean) result;
    }

    private Map<Long, Long> counts(
            String sql,
            Collection<Long> spaceIds,
            LocalDate attendanceDate
    ) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("spaceIds", spaceIds);
        query.setParameter("attendanceDate", attendanceDate);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue()
            );
        }
        return counts;
    }
}
