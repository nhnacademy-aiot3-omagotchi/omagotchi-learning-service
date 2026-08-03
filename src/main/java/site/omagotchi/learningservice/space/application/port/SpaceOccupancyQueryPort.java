package site.omagotchi.learningservice.space.application.port;

import java.time.ZonedDateTime;

public interface SpaceOccupancyQueryPort {

    boolean existsActiveOccupancy(
            Long spaceId,
            ZonedDateTime now
    );
}
