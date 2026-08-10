package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 다른 Feature가 점유 상태를 조회하는 공개 계약.
 *
 * <p>{@code space} 파트가 공간 목록의 사용 상태를 파생 계산하는 것이 첫 소비처다.
 * 공간은 "사용 중"을 컬럼으로 저장하지 않고 활성 점유의 존재로 매 조회 시 판단한다
 * (명세서 01, SSOT 원칙) — 그 판단에 필요한 사실만 여기서 제공한다.</p>
 *
 * <p>이 계약이 자리 잡으면 {@code space} 파트는
 * {@code RoomOccupancyJpaEntity}·{@code SpringDataRoomOccupancyRepository}를 지울 수 있다.
 * 지금은 같은 테이블에 엔티티가 둘 붙어 있고 "쓰기 주체는 점유 하나"라는 규약이 주석으로만
 * 강제되는 상태다.</p>
 *
 * <p>연장·반납({@link RoomOccupancyLifecycleService})과 나눈 이유는 방향이 다르기 때문이다.
 * 저쪽은 점유의 상태를 바꾸고, 여기는 읽기만 한다 — 트랜잭션 성격도 의존성도 겹치지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupancyQueryService {

    private final RoomOccupancyRepository occupancyRepository;

    /**
     * 여러 회의실의 현재 점유 상태를 한 번에 조회한다.
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 공간이 N개여도 쿼리는 1회다 — 목록을 돌며
     * 단건 조회를 부르면 기수·공간 모듈이 분리될 때 그대로 N+1 원격 호출이 된다.</p>
     *
     * <p>{@code now}를 받아 만료된 점유를 제외한다. 유니크 인덱스는 {@code status}만 보고
     * {@code expires_at}은 보지 않아서, 스케줄러(#9)가 아직 쓸어가지 않은 행이 ACTIVE로
     * 남아 있다. 이 필터가 없으면 <b>목록에는 "사용 중"인데 점유는 성공하는</b> 상태가
     * 사용자에게 보인다.</p>
     *
     * <p>결과를 {@code spaceId}로 키잡아 돌려주는 것은 소비처가 공간 목록과 조인하기
     * 때문이다. 공간당 최대 1건임은 {@code uq_room_occupancies_one_active_per_space}가
     * 보장하므로 중복 키를 걱정할 필요가 없다.</p>
     *
     * @param spaceIds 조회할 회의실. 비어 있으면 빈 결과
     * @return 사용 중인 회의실만 담긴 {@code spaceId → 점유 상태}. 비어 있는 방은 키가 없다
     */
    public Map<Long, SpaceOccupancyView> findActiveBySpaceIds(
            Collection<Long> spaceIds, OffsetDateTime now) {

        if (spaceIds == null || spaceIds.isEmpty() || now == null) {
            return Map.of();
        }
        return occupancyRepository.findActiveBySpaceIds(spaceIds, now).stream()
                .collect(Collectors.toMap(SpaceOccupancyView::spaceId, Function.identity()));
    }
}
