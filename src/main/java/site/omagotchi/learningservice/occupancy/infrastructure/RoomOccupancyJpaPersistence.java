package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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
    public boolean existsActiveBySpaceId(Long spaceId, OffsetDateTime now) {
        return occupancyJpaRepository.existsBySpaceIdAndStatusAndExpiresAtAfter(
                spaceId, OccupancyStatus.ACTIVE, now);
    }

    @Override
    public boolean existsActiveByUserId(UUID userId, OffsetDateTime now) {
        return occupancyJpaRepository.existsByOccupierUserIdAndStatusAndExpiresAtAfter(
                userId, OccupancyStatus.ACTIVE, now);
    }

    @Override
    public Optional<RoomOccupancy> findActiveBySpaceId(Long spaceId) {
        return occupancyJpaRepository.findBySpaceIdAndStatus(spaceId, OccupancyStatus.ACTIVE);
    }

    @Override
    public List<ActiveSpaceOccupancy> findActiveBySpaceIds(Collection<Long> spaceIds, OffsetDateTime now) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return List.of();
        }
        return occupancyJpaRepository.findActiveBySpaceIds(spaceIds, now, OccupancyStatus.ACTIVE);
    }

    @Override
    public Optional<ActiveOccupancy> findActiveSummaryBySpaceId(Long spaceId) {
        return occupancyJpaRepository
                .findSummaryBySpaceIdAndStatus(spaceId, OccupancyStatus.ACTIVE)
                .map(projection -> new ActiveOccupancy(
                        projection.getId(),
                        projection.getOccupierMembershipId(),
                        projection.getOccupierUserId()
                ));
    }

    /**
     * {@inheritDoc}
     *
     * <p>트랜잭션 밖에서 부르면 락이 즉시 해제되어 아무것도 보장하지 못하므로 조용히
     * 통과시키지 않고 명시적으로 막는다 ({@code SpaceNativeQueryReader#lock}과 같은 방어).</p>
     */
    @Override
    public Optional<RoomOccupancy> lockById(Long occupancyId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "점유 행 락은 트랜잭션 안에서만 획득할 수 있습니다. occupancyId=" + occupancyId);
        }
        return occupancyJpaRepository.findByIdForUpdate(occupancyId);
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