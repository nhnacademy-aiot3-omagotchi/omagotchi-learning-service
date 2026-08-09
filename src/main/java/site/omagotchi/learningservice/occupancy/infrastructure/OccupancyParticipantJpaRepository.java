package site.omagotchi.learningservice.occupancy.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

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
}
