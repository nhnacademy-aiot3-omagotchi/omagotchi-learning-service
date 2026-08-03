package site.omagotchi.learningservice.space.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Space가 기수 모듈의 내부 구조를 직접 알지 않도록 분리한 읽기 전용 계약.
 */
public interface SpaceCohortAccessPort {

    boolean exists(Long cohortId);

    boolean isSystemAdmin(String globalRole);

    boolean isActiveManager(Long cohortId, UUID userId);

    List<Long> findActiveManagedCohortIds(UUID userId);

    List<Long> findActiveCohortIds(UUID userId);
}
