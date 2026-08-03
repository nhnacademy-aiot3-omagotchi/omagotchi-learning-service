package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.application.result.SpaceListResult;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * 공간 목록과 현재 점유 상태를 조회하기 위한 출력 포트.
 *
 * 실제 DB 조회 구현은 Infrastructure 계층에서 담당한다.
 */
public interface SpaceQueryPort {

    /**
     * 삭제되지 않은 전체 공간과 현재 활성 점유 정보를 조회한다.
     *
     * @param now 활성 점유 및 남은 시간 계산 기준 시각
     * @return 공간 목록 조회 결과
     */
    List<SpaceListResult> findAllSpacesWithStatus(
            Set<Long> requesterCohortIds,
            ZonedDateTime now
    );
}
