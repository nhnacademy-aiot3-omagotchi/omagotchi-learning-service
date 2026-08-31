package site.omagotchi.learningservice.space.application.port;

import java.util.List;
import java.util.Optional;

/** 다른 Feature가 공간과 관리 주체 기수의 연결만 읽는 경계. 인가는 소비처가 담당한다. */
public interface SpaceCohortQueryPort {

    /**
     * 삭제되지 않은 공간의 기수를 조회한다.
     * 공간이 없거나 삭제됐거나 기수가 배정되지 않았으면 비어 있다.
     */
    Optional<Long> findCohortId(Long spaceId);

    /** 삭제되지 않고 해당 기수에 배정된 공간 ID를 오름차순으로 조회한다. */
    List<Long> findSpaceIdsByCohortId(Long cohortId);
}
