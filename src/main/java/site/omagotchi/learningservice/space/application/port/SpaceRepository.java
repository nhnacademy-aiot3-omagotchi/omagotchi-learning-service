package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.List;
import java.util.Optional;

public interface SpaceRepository {

    boolean existsActiveByName(String name);

    boolean existsActiveByNameAndIdNot(
            String name,
            Long spaceId
    );

    Optional<Space> findByIdForUpdate(Long spaceId);

    /**
     * 삭제되지 않은 전체 공간을 {@code id} 오름차순으로 읽는다 (RM-07).
     *
     * <p>비활성 공간도 포함한다 — 목록에는 노출하되 상태와 사유를 함께 반환하기 때문이다
     * (명세 01 §3). 걸러내는 것은 소프트 삭제뿐이다.</p>
     *
     * <p><b>점유를 조인하지 않는다.</b> 사용 상태는 저장하지 않고 활성 점유의 존재로 파생
     * 계산하는데(ADR 0003), 그 판정은 {@code occupancy}가 소유한다. 여기서 조인하면 "사용 중"의
     * 정의가 두 곳에 복제되어 만료 조건이 바뀔 때 한쪽만 고쳐진다.</p>
     */
    List<Space> findAllNotDeleted();

    Space save(Space space);

    /**
     * 이 기수가 관리 주체인 공간의 배정을 전부 해제한다 (CE-04)
     *
     * <p>해제 후에는 기수 매니저 누구나 수정·활성화·비활성화할 수 있고(RM-16), 삭제하려면
     * 먼저 인수해야 한다(RM-25) — 그 인수 경로가 {@code assignCohort}다.</p>
     *
     * @return 해제한 공간 수. 기수당 여러 개일 수 있다 (RM-26)
     */
    int unassignByCohort(Long cohortId);
}
