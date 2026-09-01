package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttendancePresenceQuery {
    List<OpenPresenceView> findOpenPresences(UUID userId);
    List<OpenUserPresenceView> findOpenPresences(Collection<UUID> userIds);
    List<OpenPresenceView> findOpenPresencesByMembershipIds(Collection<Long> membershipIds);
    List<OpenPresenceView> findOpenMeetingPresencesByMembershipIds(
            Collection<Long> membershipIds,
            Long meetingSpaceId
    );
}
