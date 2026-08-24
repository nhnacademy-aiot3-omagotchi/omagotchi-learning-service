package site.omagotchi.learningservice.realtime.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PresenceUserProfileQuery {

    Map<UUID, PresenceUserProfile> findByUserIds(Collection<UUID> userIds);
}
