package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link RoomOccupancyRepository} 구현. 존재 이유는 둘이다 — flush 시점 고정과
 * 인덱스 위반의 {@code ErrorCode} 변환.
 */
@Component
@RequiredArgsConstructor
public class RoomOccupancyJpaPersistence implements RoomOccupancyRepository {

    private final RoomOccupancyJpaRepository occupancyJpaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>{@code save}가 아니라 {@code saveAndFlush}인 것이 핵심이다. flush가 커밋 시점으로
     * 밀리면 유니크 위반이 이 메서드 밖에서 터져 아래 catch에 걸리지 않고 500이 된다.</p>
     */
    @Override
    public RoomOccupancy save(RoomOccupancy occupancy) {
        try {
            return occupancyJpaRepository.saveAndFlush(occupancy);
        } catch (DataIntegrityViolationException exception) {
            throw OccupancyConstraintTranslator.translate(exception);
        }
    }

    @Override
    public boolean existsActiveBySpaceId(Long spaceId) {
        return occupancyJpaRepository.existsBySpaceIdAndStatus(spaceId, OccupancyStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByUserId(UUID userId) {
        return occupancyJpaRepository.existsByOccupierUserIdAndStatus(userId, OccupancyStatus.ACTIVE);
    }

    @Override
    public Optional<RoomOccupancy> findActiveBySpaceId(Long spaceId) {
        return occupancyJpaRepository.findBySpaceIdAndStatus(spaceId, OccupancyStatus.ACTIVE);
    }

    @Override
    public int expireStaleBySpaceId(Long spaceId, OffsetDateTime now) {
        return occupancyJpaRepository.expireStaleBySpaceId(
                spaceId, now, OccupancyStatus.ACTIVE, OccupancyStatus.EXPIRED);
    }

    @Override
    public int expireStaleByUserId(UUID userId, OffsetDateTime now) {
        return occupancyJpaRepository.expireStaleByUserId(
                userId, now, OccupancyStatus.ACTIVE, OccupancyStatus.EXPIRED);
    }
}