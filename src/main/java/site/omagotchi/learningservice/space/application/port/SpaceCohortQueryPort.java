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

    /**
     * 공간 행을 잠근 뒤 기수를 조회한다. 삭제됐거나 기수가 없으면 비어 있다.
     *
     * <p>공간을 참조하는 <b>쓰기</b>가 뒤따를 때만 쓴다. 잠그지 않으면 공간 삭제가
     * "센서 0대"를 확인한 뒤 센서 저장이 끼어들어, 소프트 삭제된 공간에 센서가 남는다
     * — 행이 살아 있으므로 FK 로는 막지 못한다.</p>
     *
     * <p>FOR UPDATE 는 읽기 전용 트랜잭션에서 실행할 수 없다. 소비처는
     * {@code SpaceCohortWriteGuard} 를 통해서만 이 Method 에 닿는다.</p>
     */
    Optional<Long> findCohortIdByIdForUpdate(Long spaceId);

    /** 삭제되지 않고 해당 기수에 배정된 공간 ID를 오름차순으로 조회한다. */
    List<Long> findSpaceIdsByCohortId(Long cohortId);
}
