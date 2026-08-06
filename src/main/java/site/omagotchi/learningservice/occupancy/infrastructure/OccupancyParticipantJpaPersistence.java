package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link OccupancyParticipantRepository} 구현.
 *
 * <p>{@link RoomOccupancyJpaPersistence}와 같은 이유로 존재한다. 점유 시작 경로에서는
 * 이 {@code save}가 두 번째 INSERT이므로, 여기서 터지는 유니크 위반이 앞선 점유 INSERT까지
 * 롤백시킨다 — 같은 트랜잭션이라 의도한 동작이다.</p>
 */
@Component
@RequiredArgsConstructor
public class OccupancyParticipantJpaPersistence implements OccupancyParticipantRepository {

    private final OccupancyParticipantJpaRepository participantJpaRepository;

    @Override
    public OccupancyParticipant save(OccupancyParticipant participant) {
        try {
            return participantJpaRepository.saveAndFlush(participant);
        } catch (DataIntegrityViolationException exception) {
            throw OccupancyConstraintTranslator.translate(exception);
        }
    }

    @Override
    public long countActiveByOccupancyId(Long occupancyId) {
        return participantJpaRepository.countByOccupancyIdAndLeftAtIsNull(occupancyId);
    }

    @Override
    public Optional<OccupancyParticipant> find(Long occupancyId, UUID userId) {
        return participantJpaRepository.findByOccupancyIdAndUserId(occupancyId, userId);
    }
}
