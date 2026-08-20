package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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
     *
     * <p>{@code now}로 만료된 행을 제외하는 것이 계약의 일부다. {@code status}만 보면
     * 스케줄러(#9)가 아직 쓸어가지 않은 만료 행이 "사용 중"으로 잡힌다 —
     * {@link #findActiveBySpaceIds}와 판정 기준이 갈리면 같은 방이 목록에서는 공실인데
     * 여기서는 사용 중이 된다.</p>
     */
    boolean existsActiveBySpaceId(Long spaceId, OffsetDateTime now);


    /**
     * 이 계정이 이미 어딘가를 점유 중인가 (MR-10).
     *
     * <p>멤버십이 아니라 계정 기준인 것이 핵심이다. 다기수 담당자도 방을 둘 잡을 수 없다.
     * 다만 이 검사는 {@code spaces} 락으로 직렬화되지 않는다 — 서로 다른 방을 동시에
     * 잡으려는 같은 계정은 락 대상이 달라 둘 다 통과한다. 그 경우
     * {@code uq_room_occupancies_one_active_per_user}가 유일한 방어선이다.</p>
     *
     * <p>{@link #existsActiveBySpaceId}와 같은 이유로 {@code now}를 받는다. 여기서
     * 만료 행을 세면 이미 끝난 점유 때문에 새 점유가 막힌다.</p>
     */
    boolean existsActiveByUserId(UUID userId, OffsetDateTime now);


    /** 활성 점유 단건. 연장·반납(#7)의 진입점이다. */
    Optional<RoomOccupancy> findActiveBySpaceId(Long spaceId);


    /**
     * 여러 공간의 활성 점유를 한 번에 읽는다. 공간 목록의 사용 상태 파생 계산용이다.
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 공간이 N개여도 쿼리는 1회여야 한다 —
     * 목록을 돌며 {@link #findActiveBySpaceId}를 부르면 그대로 N+1이 된다.</p>
     *
     * <p>{@code now}로 만료된 행을 걸러내는 것이 중요하다. 유니크 인덱스는 {@code status}만
     * 보고 {@code expires_at}은 보지 않으므로, 스케줄러(#9)가 아직 쓸어가지 않은 행이
     * ACTIVE로 남아 있다 — 이 필터가 없으면 목록에 "사용 중"으로 뜨는데 점유는 성공하는
     * 상태가 보인다.</p>
     *
     * <p>공간당 최대 1건이다 — {@code uq_room_occupancies_one_active_per_space}가
     * 보장한다. 받는 쪽이 중복을 걱정할 필요가 없다.</p>
     *
     * <p>점유자의 <b>기수가 아니라 멤버십 식별자</b>를 돌려주는 것이 요점이다. 점유 행에
     * {@code cohort_id}가 없어(ERD v3) 기수는 {@code cohort_memberships} 조인으로만
     * 나오는데, 그 테이블은 기수 파트 소유라 여기서 조인하면 안 된다. 기수 변환은
     * {@code OccupancyQueryService}가 기수 파트의 공개 계약으로 처리한다.</p>
     */
    List<ActiveSpaceOccupancy> findActiveBySpaceIds(Collection<Long> spaceIds, OffsetDateTime now);


    /**
     * 활성 점유의 식별 정보만 값으로 읽는다. 락 밖 사전 검증에 쓴다.
     *
     * <p>{@link #findActiveBySpaceId}가 아니라 이 메서드를 쓰는 이유가 중요하다.
     * 엔티티를 락 전에 읽으면 그 인스턴스가 영속성 컨텍스트 1차 캐시에 올라가고,
     * 뒤이은 {@link #lockById}가 {@code FOR UPDATE}를 실제로 실행해도 Hibernate는
     * 캐시 인스턴스를 그대로 돌려준다. 그러면 락 획득 후 상태 재확인이 락 이전
     * 스냅샷을 보게 되어 "종료 커밋 직후 도착한 요청" 방어가 무력화된다
     * ({@code SpaceAccessQueryPort#lock} 주석과 같은 함정).</p>
     */
    Optional<ActiveOccupancy> findActiveSummaryBySpaceId(Long spaceId);


    /**
     * 점유 행 배타 락. 반드시 트랜잭션 안에서 호출한다.
     *
     * <p>활성 조건을 쿼리에 넣지 않는 것이 의도다. 락을 잡은 뒤
     * {@link RoomOccupancy#isActive()}를 확인해야 종료 커밋 직후 도착한 요청을
     * "이미 종료된 점유"(409)로 정확히 잡는다. 조건에 넣으면 그냥 행 없음으로 빠진다.</p>
     *
     * <p>참여자 추가·이탈·제외가 이 락으로 시작하는 이유는 정원 검증 때문만이 아니다.
     * 락 없이 처리하면 종료된 점유에 {@code left_at IS NULL} 행이 남아, 그 사용자가
     * 영구히 다른 회의에 참여할 수 없게 된다 —
     * {@code uq_occupancy_participants_one_active}가 계정 기준이기 때문이다.</p>
     */
    Optional<RoomOccupancy> lockById(Long occupancyId);


    /**
     * 만료 임박 알림 후보를 찾는다 (MR-12).
     *
     * <p>후보 조건은 ACTIVE, {@code ended_at IS NULL},
     * {@code now < expires_at <= reminderEndsAt}, {@code reminder_sent_at IS NULL}이다.
     * 조회 뒤 연장·반납·다른 인스턴스의 발송이 끼어들 수 있으므로, 호출부는 행을 잠근 뒤
     * 현재 상태와 후보의 만료 시각을 다시 확인해야 한다.</p>
     */
    List<ExpiringOccupancy> findExpiringSoon(
            OffsetDateTime now, OffsetDateTime reminderEndsAt);


    /**
     * 참여자 관리에 필요한 점유 정보.
     *
     * <p>기수를 담지 않는다. 점유 행에 {@code cohort_id}가 없고(ERD v3) 기수는
     * {@code occupierMembershipId} 조인으로 도출하므로, 기수 정합 검증(MR-33)은
     * 이 값을 받아 기수 파트에 되묻는다.</p>
     */
    record ActiveOccupancy(Long id, Long occupierMembershipId, UUID occupierUserId) {
    }

    /** 만료 임박 조회 시점의 후보. {@code expiresAt}은 연장 경합 재검증에 사용한다. */
    record ExpiringOccupancy(Long occupancyId, OffsetDateTime expiresAt) {
    }


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
     * <p><b>정리된 점유를 돌려주는 것이 계약의 일부다.</b> 점유 행만 EXPIRED로 바꾸면
     * 그 안의 참여자가 열린 채 남고, {@code uq_occupancy_participants_one_active}가 계정
     * 기준이라 그 사람들이 영구히 다른 회의에 참여할 수 없게 된다 (MR-32). 호출부가
     * 반환값으로 {@link OccupancyParticipantRepository#closeAllActiveByOccupancyId}를
     * 이어서 불러야 하며, 두 테이블을 한 Port에서 함께 쓰지 않기 위해 마감은 여기서
     * 하지 않는다.</p>
     *
     * @return 이번 호출로 EXPIRED가 된 점유. 없으면 빈 목록
     */
    List<ExpiredOccupancy> expireStaleBySpaceId(Long spaceId, OffsetDateTime now);


    /**
     * 요청자 계정의 만료된 ACTIVE 행을 정리한다.
     *
     * <p>{@link #expireStaleBySpaceId}와 달리 {@code spaces} 락 밖의 다른 방일 수 있어
     * 완전히 직렬화되지 않는다. 정리에 실패해도 유니크 인덱스가 최종 방어선이므로
     * 정확성은 유지되고, 이 호출은 불필요한 409를 줄이는 최선 노력이다.</p>
     *
     * <p>{@link #expireStaleBySpaceId}와 같은 이유로 정리된 점유를 돌려준다.</p>
     */
    List<ExpiredOccupancy> expireStaleByUserId(UUID userId, OffsetDateTime now);


    /**
     * 만료됐지만 아직 ACTIVE인 행을 공간·계정 구분 없이 찾는다 (스케줄러 #9).
     *
     * <p>{@link #expireStaleBySpaceId}·{@link #expireStaleByUserId}가 <b>대체하지 못하는</b>
     * 경로다. 저 둘은 누군가 그 공간을 점유하려 하거나 그 계정이 새로 점유할 때만 돌아서,
     * 아무도 오지 않는 방은 만료돼도 계속 ACTIVE로 남는다 — 공간 목록에 "사용 중"으로
     * 뜨고 참여자도 열린 채다.</p>
     *
     * <p><b>찾기만 하고 바꾸지 않는다.</b> 상태 전이는 {@link #expire}가 건별로 한다 —
     * 명세서 03이 "한 건의 실패가 나머지를 막지 않도록 건별로 처리한다"고 정했고,
     * 한 UPDATE로 묶으면 그 격리가 성립하지 않는다.</p>
     */
    List<ExpiredOccupancy> findStale(OffsetDateTime now);


    /**
     * 점유 한 건을 EXPIRED로 전이한다 (스케줄러 #9).
     *
     * <p>조건이 {@code status = 'ACTIVE' AND expires_at <= now}인 것이 핵심이다.
     * 조회와 전이 사이에 벌어진 일을 이 조건이 걸러낸다.</p>
     * <ul>
     *   <li><b>연장</b>: {@code expires_at}이 미래로 밀려 조건에 맞지 않는다 — 사용 중인
     *       회의가 스케줄러에 끊기지 않는다 (명세서 03 §5 "만료와 연장 경합")</li>
     *   <li><b>반납·강제 종료</b>: {@code status}가 이미 ACTIVE가 아니라 종료 사유를
     *       EXPIRED로 덮어쓰지 않는다</li>
     *   <li><b>다른 인스턴스가 먼저 처리</b>: 같은 이유로 0행이 된다. 행 락으로 직렬화되므로
     *       둘 중 하나만 {@code true}를 받아 <b>공실 알림이 중복 발행되지 않는다</b></li>
     * </ul>
     *
     * <p>{@code endedAt}을 인자로 받지 않고 그 행의 {@code expires_at}을 쓰는 것이 요점이다 —
     * 실제 종료 시각이 그것이고, {@code ck_room_occupancies_end}도 함께 만족한다.</p>
     *
     * @return 이번 호출로 전이됐으면 {@code true}. {@code false}면 위 사유 중 하나이며
     *         <b>참여자 마감도 이벤트 발행도 하면 안 된다</b>
     */
    boolean expire(Long occupancyId, OffsetDateTime now);


    /**
     * 만료 정리로 종료된 점유.
     *
     * @param spaceId 비워진 회의실. 공실 알림 발행에 필요하다 — 알림 대상은 공간 기준이다
     * @param endedAt 종료 시각. 정리를 수행한 시각이 아니라 그 점유의 {@code expires_at}이다.
     *                참여자 {@code left_at}과 {@code RoomVacatedEvent.vacatedAt}도 같은 값이어야
     *                "회의가 끝난 시각"이 테이블과 알림에서 일치한다
     */
    record ExpiredOccupancy(Long occupancyId, Long spaceId, OffsetDateTime endedAt) {
    }

}
