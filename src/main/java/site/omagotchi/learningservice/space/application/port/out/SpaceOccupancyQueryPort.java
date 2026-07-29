package site.omagotchi.learningservice.space.application.port.out;

import java.time.ZonedDateTime;

public interface SpaceOccupancyQueryPort {

    boolean existsActiveOccupancy(
            Long spaceId,
            ZonedDateTime now
    );
}
