package site.omagotchi.learningservice.space.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.out.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;

import java.time.ZonedDateTime;

@Repository
@RequiredArgsConstructor
public class SpaceOccupancyQueryJpaAdapter
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
