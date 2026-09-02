package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.attendance.application.result.PresenceIntervalView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AttendancePresenceJpaQuery implements AttendancePresenceQuery {
    private final PresenceIntervalRepository presenceIntervalRepository;

    @Override public List<CurrentPresenceResult> findCurrentPresences(
            Long cohortMembershipId,
            LocalDate attendanceDate
    ) {
        return presenceIntervalRepository.findCurrentPresences(
                cohortMembershipId,
                attendanceDate
        );
    }

    @Override public List<OpenPresenceView> findOpenPresences(UUID userId) {
        return presenceIntervalRepository.findOpenPresences(userId);
    }

    @Override public List<OpenUserPresenceView> findOpenPresences(Collection<UUID> userIds) {
        return presenceIntervalRepository.findOpenPresences(userIds);
    }

    @Override public List<PresenceIntervalView> findPresenceIntervals(
            Long cohortMembershipId,
            Instant from,
            Instant toExclusive
    ) {
        return presenceIntervalRepository.findPresenceIntervals(cohortMembershipId, from, toExclusive);
    }

    @Override public List<OpenPresenceView> findOpenPresencesByMembershipIds(Collection<Long> membershipIds) {
        return presenceIntervalRepository.findOpenPresencesByMembershipIds(membershipIds);
    }

    @Override public List<OpenPresenceView> findOpenMeetingPresencesByMembershipIds(
            Collection<Long> membershipIds,
            Long meetingSpaceId
    ) {
        return presenceIntervalRepository.findOpenMeetingPresencesByMembershipIds(
                membershipIds,
                meetingSpaceId
        );
    }
}
