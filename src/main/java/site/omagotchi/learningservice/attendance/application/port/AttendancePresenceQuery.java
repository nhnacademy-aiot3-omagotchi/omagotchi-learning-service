package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.attendance.application.result.PresenceIntervalView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttendancePresenceQuery {
    /** 소속의 현재 집계일 구간과 집계일을 넘긴 열린 회의를 우선순위순으로 조회한다. */
    List<CurrentPresenceResult> findCurrentPresences(
            Long cohortMembershipId,
            LocalDate attendanceDate
    );

    List<OpenPresenceView> findOpenPresences(UUID userId);
    List<OpenUserPresenceView> findOpenPresences(Collection<UUID> userIds);

    /** 소속의 체류 구간을 기간으로 조회한다. 범위와 겹치는 구간을 모두 돌려준다. */
    List<PresenceIntervalView> findPresenceIntervals(
            Long cohortMembershipId,
            Instant from,
            Instant toExclusive
    );
    List<OpenPresenceView> findOpenPresencesByMembershipIds(Collection<Long> membershipIds);
    List<OpenPresenceView> findOpenMeetingPresencesByMembershipIds(
            Collection<Long> membershipIds,
            Long meetingSpaceId
    );
}
