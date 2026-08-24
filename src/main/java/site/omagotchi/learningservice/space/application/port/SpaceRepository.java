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
     * 이 기수에 배정된 실습실의 배정을 전부 해제한다 (CE-04).
     *
     * <p><b>{@code LAB} 필터가 안전선이다.</b> 빼면 그 기수가 생성한 회의실·독서실의 관리
     * 주체까지 {@code NULL}이 되어, 삭제 권한이 관리 주체 기수 기준(RM-25)이라 <b>아무도
     * 지울 수 없는 공간</b>이 생긴다. 회의실·독서실의 기수는 기수 종료 후에도 유지한다
     * (명세 08 §2 4단계).</p>
     *
     * <p>이 해제를 빠뜨리면 실습실이 배정 상태로 남아 다음 기수 배정이 409로 막힌다.</p>
     *
     * @return 해제한 실습실 수. 기수당 여러 개일 수 있다 (RM-26)
     */
    int unassignLabsByCohort(Long cohortId);
}
