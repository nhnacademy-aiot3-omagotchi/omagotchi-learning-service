package site.omagotchi.learningservice.occupancy.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

public interface OccupancyParticipantJpaRepository extends JpaRepository<OccupancyParticipant, Long> {

    /** {@code left_at IS NULL}인 참여자 수. 정원 검증(MR-28)의 기준이다. */
    long countByOccupancyIdAndLeftAtIsNull(Long occupancyId);
}
