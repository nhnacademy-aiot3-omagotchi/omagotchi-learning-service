package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code room_occupancies} Persistence 경계.
 *
 * <p>종료 상태의 행도 이력으로 보존하므로 "행이 있다"와 "사용 중이다"가 다르다.
 * 조회 메서드에 활성 필터가 붙어 있는지 항상 확인한다.</p>
 */
public interface RoomOccupancyRepository {

    /**
     * 점유 행을 저장하고 즉시 flush한다.
     *
     * <p>flush를 지연하지 않는 것이 계약의 일부다. 커밋 시점까지 밀면 유니크 위반이
     * 이 메서드 밖에서 터져 {@code ErrorCode}로 변환되지 못하고 500이 된다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         {@code uq_room_occupancies_one_active_per_space} 위반 시
     *         {@code OCCUPANCY_ROOM_ALREADY_OCCUPIED}(409),
     *         {@code uq_room_occupancies_one_active_per_user} 위반 시
     *         {@code OCCUPANCY_ALREADY_OCCUPYING}(409)
     */
    RoomOccupancy save(RoomOccupancy occupancy);


    /**
     * 이 공간에 활성 점유가 있는가 (MR-09).
     *
     * <p>반드시 {@code spaces} 행 락을 잡은 트랜잭션 안에서 호출한다.
     * 락 밖에서 확인하면 두 요청이 동시에 "없음"을 보고 둘 다 INSERT를 시도한다 —
     * 그 경우는 유니크 인덱스가 막지만 예외 경로로 빠진다.</p>
     */
    boolean existsActiveBySpaceId(Long spaceId);


    /**
     * 이 계정이 이미 어딘가를 점유 중인가 (MR-10).
     *
     * <p>멤버십이 아니라 계정 기준인 것이 핵심이다. 다기수 담당자도 방을 둘 잡을 수 없다.
     * 다만 이 검사는 {@code spaces} 락으로 직렬화되지 않는다 — 서로 다른 방을 동시에
     * 잡으려는 같은 계정은 락 대상이 달라 둘 다 통과한다. 그 경우
     * {@code uq_room_occupancies_one_active_per_user}가 유일한 방어선이다.</p>
     */
    boolean existsActiveByUserId(UUID userId);


    /** 활성 점유 단건. 연장·반납(#7)의 진입점이다. */
    Optional<RoomOccupancy> findActiveBySpaceId(Long spaceId);


    /**
     * 만료됐지만 아직 ACTIVE로 남아 있는 행을 EXPIRED로 정리한다.
     *
     * <p>스케줄러(#9)가 아직 없어서가 아니라, 있어도 필요한 코드다. 유니크 인덱스는
     * {@code status = 'ACTIVE'}만 보고 {@code expires_at}은 보지 않으므로, 스케줄러
     * 주기 사이에는 "공간 목록에는 사용 가능인데 점유하면 409"인 창이 생긴다.
     * 점유 시작이 이미 {@code spaces} 행 락을 잡고 있으므로 여기서 함께 정리한다.</p>
     *
     * <p>{@code ended_at}에 {@code now}가 아니라 {@code expires_at}을 넣는 것이 요점이다 —
     * 실제 종료 시각이 그것이고, {@code ck_room_occupancies_end}도 함께 만족한다.</p>
     *
     * @return 정리된 행 수
     */
    int expireStaleBySpaceId(Long spaceId, OffsetDateTime now);


    /**
     * 요청자 계정의 만료된 ACTIVE 행을 정리한다.
     *
     * <p>{@link #expireStaleBySpaceId}와 달리 {@code spaces} 락 밖의 다른 방일 수 있어
     * 완전히 직렬화되지 않는다. 정리에 실패해도 유니크 인덱스가 최종 방어선이므로
     * 정확성은 유지되고, 이 호출은 불필요한 409를 줄이는 최선 노력이다.</p>
     */
    int expireStaleByUserId(UUID userId, OffsetDateTime now);

}
