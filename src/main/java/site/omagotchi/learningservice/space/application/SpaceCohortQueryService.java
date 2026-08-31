package site.omagotchi.learningservice.space.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.space.application.port.SpaceCohortQueryPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 다른 Feature가 공간의 관리 주체 기수를 읽는 공개 계약.
 *
 * <p>조치 알림의 수신자 판정이 첫 소비처다. 알림은 그 공간을 담당하는 기수의 매니저에게만
 * 가야 하는데, 센서 이벤트는 공간까지만 알고 기수는 모른다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceCohortQueryService {

    private final SpaceCohortQueryPort spaceCohortQueryPort;

    public Optional<Long> findCohortId(Long spaceId) {
        if (Objects.isNull(spaceId)) {
            return Optional.empty();
        }
        return spaceCohortQueryPort.findCohortId(spaceId);
    }

    public List<Long> findSpaceIdsByCohortId(Long cohortId) {
        return spaceCohortQueryPort.findSpaceIdsByCohortId(
                Objects.requireNonNull(cohortId, "cohortId")
        );
    }
}
