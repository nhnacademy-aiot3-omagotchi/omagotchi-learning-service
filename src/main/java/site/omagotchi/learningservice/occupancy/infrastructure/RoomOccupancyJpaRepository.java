package site.omagotchi.learningservice.occupancy.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code room_occupancies}의 Spring Data 접근. Application은 이 인터페이스를 보지 않는다 —
 * {@code RoomOccupancyRepository} Port를 통하고, 그 구현은 {@link RoomOccupancyJpaPersistence}다.
 *
 * <p>Port가 아니라 여기에 {@code JpaRepository}가 붙는 이유는 flush 시점과 인덱스 위반
 * 변환이 기술 세부사항이기 때문이다.</p>
 *
 * <p>테스트는 이 인터페이스를 직접 써도 된다 — 의존 방향 규칙은 {@code src/main}에만 적용된다.</p>
 */
public interface RoomOccupancyJpaRepository extends JpaRepository<RoomOccupancy, Long> {

    /**
     * {@code status}와 {@code expiresAt}을 함께 본다.
     *
     * <p>{@code existsBySpaceIdAndStatus}로 두면 만료됐지만 스케줄러(#9)가 아직 EXPIRED로
     * 바꾸지 않은 행이 "사용 중"으로 잡힌다. {@link #findActiveBySpaceIds}가 이미
     * {@code expiresAt > now}로 거르고 있어 조건을 맞추지 않으면 같은 방의 판정이 갈린다.</p>
     */
    boolean existsBySpaceIdAndStatusAndExpiresAtAfter(
            Long spaceId, OccupancyStatus status, OffsetDateTime now);

    boolean existsByOccupierUserIdAndStatusAndExpiresAtAfter(
            UUID occupierUserId, OccupancyStatus status, OffsetDateTime now);

    Optional<RoomOccupancy> findBySpaceIdAndStatus(Long spaceId, OccupancyStatus status);

    /**
     * 활성 점유의 식별 정보만 읽는다.
     *
     * <p>Interface Projection인 것이 요점이다. 닫힌 Projection은 나열한 컬럼만 select하고
     * 엔티티를 영속성 컨텍스트에 올리지 않으므로, 뒤이은 {@link #findByIdForUpdate}가
     * 1차 캐시가 아니라 실제 {@code FOR UPDATE} 결과를 돌려준다. 엔티티로 읽으면
     * 락 후 상태 재확인이 락 이전 스냅샷을 보게 된다.</p>
     */
    Optional<ActiveOccupancyProjection> findSummaryBySpaceIdAndStatus(
            Long spaceId, OccupancyStatus status);

    /**
     * 점유 행 배타 락.
     *
     * <p>{@code status} 조건을 쿼리에 넣지 않는 것이 의도다 — 락을 잡은 뒤 확인해야
     * 종료 커밋 직후 도착한 요청을 409로 정확히 잡는다 ({@code TeamJpaRepository}의
     * {@code findByIdForUpdate}와 같은 규약).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RoomOccupancy o where o.id = :id")
    Optional<RoomOccupancy> findByIdForUpdate(@Param("id") Long id);

    /**
     * 여러 공간의 활성 점유를 배치로 읽는다 (공간 목록의 사용 상태 계산용).
     *
     * <p>{@code expiresAt > now} 조건이 스케줄러 공백을 메운다 — 만료됐지만 아직
     * ACTIVE인 행을 "사용 중"으로 보여주지 않는다.</p>
     */
    @Query("""
                SELECT new site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy(
                           o.id, o.spaceId, o.expiresAt, o.occupierMembershipId, o.occupierUserId)
                  FROM RoomOccupancy o
                 WHERE o.spaceId IN :spaceIds
                   AND o.status = :active
                   AND o.expiresAt > :now""")
    List<ActiveSpaceOccupancy> findActiveBySpaceIds(
            @Param("spaceIds") Collection<Long> spaceIds,
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus active
    );

    /** 닫힌 Projection. 필드를 늘리면 select 컬럼이 함께 늘어난다. */
    interface ActiveOccupancyProjection {
        Long getId();

        Long getOccupierMembershipId();

        UUID getOccupierUserId();
    }

    /**
     * 정리 대상이 될 만료 행을 미리 식별한다.
     *
     * <p>벌크 UPDATE가 무엇을 바꿨는지 돌려주지 않기 때문에 필요하다. 참여자 마감(MR-32)은
     * 점유 식별자와 종료 시각을 알아야 하는데, UPDATE 뒤에는 조건({@code status='ACTIVE'})이
     * 더 이상 그 행에 맞지 않아 다시 찾을 수 없다.</p>
     *
     * <p><b>Projection인 것이 중요하다.</b> 엔티티로 읽으면 그 인스턴스가 1차 캐시에 올라가고,
     * 뒤이은 벌크 UPDATE가 영속성 컨텍스트를 우회하므로 캐시가 낡은 {@code status}를
     * 들고 있게 된다.</p>
     */
    @Query("""
                SELECT o.id AS occupancyId, o.spaceId AS spaceId, o.expiresAt AS endedAt
                  FROM RoomOccupancy o
                 WHERE o.spaceId = :spaceId
                   AND o.status = :active
                   AND o.expiresAt <= :now""")
    List<StaleOccupancyProjection> findStaleBySpaceId(
            @Param("spaceId") Long spaceId,
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus active
    );

    @Query("""
                SELECT o.id AS occupancyId, o.spaceId AS spaceId, o.expiresAt AS endedAt
                  FROM RoomOccupancy o
                 WHERE o.occupierUserId = :userId
                   AND o.status = :active
                   AND o.expiresAt <= :now""")
    List<StaleOccupancyProjection> findStaleByUserId(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus active
    );

    /**
     * 만료된 ACTIVE 행 전부 (스케줄러 #9).
     *
     * <p>정렬을 두는 것은 정리 순서를 재현 가능하게 만들기 위해서다 — 실패해 재시도할 때
     * 같은 순서로 처리된다.</p>
     */
    @Query("""
                SELECT o.id AS occupancyId, o.spaceId AS spaceId, o.expiresAt AS endedAt
                  FROM RoomOccupancy o
                 WHERE o.status = :active
                   AND o.expiresAt <= :now
                 ORDER BY o.id ASC""")
    List<StaleOccupancyProjection> findStale(
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus active
    );

    /** 닫힌 Projection. 필드를 늘리면 select 컬럼이 함께 늘어난다. */
    interface StaleOccupancyProjection {
        Long getOccupancyId();

        Long getSpaceId();

        OffsetDateTime getEndedAt();
    }

    /**
     * 만료된 ACTIVE 행 정리.
     *
     * <p>{@code endedAt}에 {@code now}가 아니라 {@code expiresAt}을 넣는 것이 요점이다 —
     * 실제 종료 시각이 그것이고, {@code ck_room_occupancies_end}
     * ({@code (status='ACTIVE') = (ended_at IS NULL)})도 함께 만족한다.</p>
     *
     * <p>벌크 UPDATE는 영속성 컨텍스트를 우회하므로 {@code clearAutomatically}가 필요해 보이지만,
     * 이 호출 시점에는 아직 어떤 점유 엔티티도 로드하지 않았다. 호출 순서를 바꾸면
     * (예: 활성 점유를 엔티티로 먼저 읽고 정리) 1차 캐시가 낡은 상태를 들고 있게 된다.
     * 바로 위 {@code findStale*}가 Projection인 것도 같은 이유다.</p>
     */
    @Modifying
    @Query("""
                UPDATE RoomOccupancy o
                SET o.status = :expired, o.endedAt = o.expiresAt
                WHERE o.spaceId = :spaceId
                  AND o.status = :active
                  AND o.expiresAt <= :now""")
    int expireStaleBySpaceId(
            @Param("spaceId") Long spaceId,
            @Param("now")OffsetDateTime now,
            @Param("active") OccupancyStatus active,
            @Param("expired") OccupancyStatus expired
            );

    @Modifying
    @Query("""
                UPDATE RoomOccupancy o
                SET o.status = :expired, o.endedAt = o.expiresAt
                WHERE o.occupierUserId = :userId
                AND o.status = :active
                AND o.expiresAt <= :now
                """)
    int expireStaleByUserId(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus  active,
            @Param("expired") OccupancyStatus expired
    );

    /**
     * 점유 한 건만 EXPIRED로 전이한다 (스케줄러 #9).
     *
     * <p>조건에 {@code expiresAt <= now}가 남아 있는 것이 핵심이다. 조회 시점에는 만료였어도
     * 그 사이 점유자가 연장했으면 조건에 맞지 않아 0행이 되고, 사용 중인 회의가 스케줄러에
     * 끊기지 않는다. {@code status} 조건은 반납·강제 종료가 이미 찍은 종료 사유를
     * EXPIRED로 덮어쓰지 않게 한다.</p>
     *
     * <p>영향 행 수를 그대로 돌려주는 것이 계약이다 — 호출부가 이 값으로 참여자 마감과
     * 이벤트 발행 여부를 정한다.</p>
     */
    @Modifying
    @Query("""
                UPDATE RoomOccupancy o
                SET o.status = :expired, o.endedAt = o.expiresAt
                WHERE o.id = :id
                AND o.status = :active
                AND o.expiresAt <= :now
                """)
    int expireById(
            @Param("id") Long id,
            @Param("now") OffsetDateTime now,
            @Param("active") OccupancyStatus active,
            @Param("expired") OccupancyStatus expired
    );
}
