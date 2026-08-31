package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AttendancePresenceJpaQuery implements AttendancePresenceQuery {
    private final PresenceIntervalRepository presenceIntervalRepository;
    @Override public List<OpenPresenceView> findOpenPresences(UUID userId) {
        return presenceIntervalRepository.findOpenPresences(userId);
    }
    @Override public List<OpenUserPresenceView> findOpenPresences(Collection<UUID> userIds) {
        return presenceIntervalRepository.findOpenPresences(userIds);
    }
}
