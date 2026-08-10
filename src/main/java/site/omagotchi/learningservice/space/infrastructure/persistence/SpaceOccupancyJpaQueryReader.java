package site.omagotchi.learningservice.space.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;

import java.time.ZonedDateTime;

@Repository
@RequiredArgsConstructor
public class SpaceOccupancyJpaQueryReader
        implements SpaceOccupancyQueryPort {

    private final SpringDataRoomOccupancyRepository repository;

    @Override
    public boolean existsActiveOccupancy(
            Long spaceId,
            ZonedDateTime now
    ) {
        return repository.existsActiveBySpaceId(
                spaceId,
                now.toOffsetDateTime()
        );
    }
}
