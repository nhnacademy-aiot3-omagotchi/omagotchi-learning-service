package site.omagotchi.learningservice.space.application.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.out.SpaceQueryPort;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 공간 목록과 현재 상태를 조회하는 Application Service.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceQueryPort spaceQueryPort;
    private final Clock clock;

    /**
     * 전체 활성 공간과 현재 사용 상태를 조회한다.
     */
    public List<SpaceListItem> getSpaceList() {
        ZonedDateTime now = ZonedDateTime.now(clock);

        return spaceQueryPort.findAllSpacesWithStatus(now);
    }
}