package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 공간 목록과 현재 상태를 조회하는 Application Service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceQueryPort spaceQueryPort;
    private final SpaceCohortAccessPort cohortAccessPort;
    private final Clock clock;

    public List<SpaceListResult> getSpaceList(UUID requesterUserId) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        Set<Long> requesterCohortIds = requesterUserId == null
                ? Set.of()
                : Set.copyOf(cohortAccessPort.findActiveCohortIds(
                        requesterUserId
                ));

        return spaceQueryPort.findAllSpacesWithStatus(
                requesterCohortIds,
                now
        );
    }
}
