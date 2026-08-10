package site.omagotchi.learningservice.occupancy.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OccupancyParticipantJpaRepository extends JpaRepository<OccupancyParticipant, Long> {

    /** {@code left_at IS NULL}인 참여자 수. 정원 검증(MR-28)의 기준이다. */
    long countByOccupancyIdAndLeftAtIsNull(Long occupancyId);

    /**
     * 이탈 여부와 무관하게 조회한다. 재합류가 기존 행의 {@code left_at}을 되돌리는
     * 방식이라(결정 #30) 이미 이탈한 행도 찾아야 한다.
     */
    Optional<OccupancyParticipant> findByOccupancyIdAndUserId(Long occupancyId, UUID userId);

    /**
     * 여러 점유의 현재 참여자를 배치로 읽는다 (공간 목록의 참여자 표시용).
     *
     * <p>엔티티가 아니라 Projection인 것이 의도다. 필요한 것은 {@code occupancy_id}와
     * {@code user_id} 둘뿐인데 엔티티로 읽으면 참여자 전원이 영속성 컨텍스트에 올라간다 —
     * 조회 전용 경로에서 더티 체킹 대상을 만들 이유가 없다.</p>
     *
     * <p>{@code id} 오름차순이 참여 순서다. 정렬을 빼면 표시 순서가 매 조회마다 달라진다.</p>
     */
    @Query("""
                SELECT p.occupancyId AS occupancyId, p.userId AS userId
                  FROM OccupancyParticipant p
                 WHERE p.occupancyId IN :occupancyIds
                   AND p.leftAt IS NULL
                 ORDER BY p.id ASC""")
    List<ActiveParticipantProjection> findActiveByOccupancyIds(
            @Param("occupancyIds") Collection<Long> occupancyIds
    );

    /** 닫힌 Projection. 필드를 늘리면 select 컬럼이 함께 늘어난다. */
    interface ActiveParticipantProjection {
        Long getOccupancyId();

        UUID getUserId();
    }

    /**
     * 열린 참여자 전원 마감 (MR-32).
     *
     * <p>벌크 UPDATE는 영속성 컨텍스트를 우회하므로 {@code clearAutomatically}가 필요해
     * 보이지만, 반납 경로는 참여자를 엔티티로 읽지 않는다. 호출 순서를 바꾸면
     * (예: 참여자를 먼저 조회하고 마감) 1차 캐시가 낡은 상태를 들고 있게 된다 —
     * {@code RoomOccupancyJpaRepository.expireStaleBySpaceId}와 같은 전제다.</p>
     *
     * <p>{@code WHERE left_at IS NULL} 조건부 UPDATE라 멱등하다. 같은 반납이 두 번
     * 처리돼도 이미 닫힌 행의 시각을 덮어쓰지 않는다.</p>
     */
    @Modifying
    @Query("""
                UPDATE OccupancyParticipant p
                SET p.leftAt = :endedAt
                WHERE p.occupancyId = :occupancyId
                  AND p.leftAt IS NULL""")
    int closeAllActiveByOccupancyId(
            @Param("occupancyId") Long occupancyId,
            @Param("endedAt") OffsetDateTime endedAt
    );
}
