package site.omagotchi.learningservice.occupancy.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
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

    boolean existsBySpaceIdAndStatus(Long spaceId, OccupancyStatus status);

    boolean existsByOccupierUserIdAndStatus(UUID occupierUserId, OccupancyStatus status);

    Optional<RoomOccupancy> findBySpaceIdAndStatus(Long spaceId, OccupancyStatus status);

    /**
     * 만료된 ACTIVE 행 정리.
     *
     * <p>{@code endedAt}에 {@code now}가 아니라 {@code expiresAt}을 넣는 것이 요점이다 —
     * 실제 종료 시각이 그것이고, {@code ck_room_occupancies_end}
     * ({@code (status='ACTIVE') = (ended_at IS NULL)})도 함께 만족한다.</p>
     *
     * <p>벌크 UPDATE는 영속성 컨텍스트를 우회하므로 {@code clearAutomatically}가 필요해 보이지만,
     * 이 호출 시점에는 아직 어떤 점유 엔티티도 로드하지 않았다. 호출 순서를 바꾸면
     * (예: 활성 점유를 엔티티로 먼저 읽고 정리) 1차 캐시가 낡은 상태를 들고 있게 된다.</p>
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
}
