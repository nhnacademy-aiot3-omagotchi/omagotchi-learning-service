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

    /**
     * {@inheritDoc}
     *
     * <p>식별(조회)과 전이(단건 조건부 UPDATE)를 분리한다. 후보를 찾아둔 뒤 그 사이 다른
     * 요청이 반납·연장을 커밋하면, 후보의 {@code status}·{@code expires_at}은 더 이상
     * {@link #expire}의 조건({@code status='ACTIVE' AND expires_at <= now})과 맞지 않아
     * 0행으로 끝난다 — 그 건은 결과에서 제외되어 참여자 마감·이벤트 발행 대상이 되지
     * 않는다. 벌크 UPDATE로 한 번에 바꾸고 조회 결과를 그대로 반환하면, 그 사이 벌어진
     * 반납·연장을 놓치고 이미 처리된(또는 여전히 사용 중인) 점유를 정리했다고 잘못
     * 보고하게 된다.</p>
     */
    @Override
    public List<ExpiredOccupancy> expireStaleBySpaceId(Long spaceId, OffsetDateTime now) {
        return expireCandidates(
                occupancyJpaRepository.findStaleBySpaceId(spaceId, now, OccupancyStatus.ACTIVE), now);
    }

    @Override
    public List<ExpiredOccupancy> expireStaleByUserId(UUID userId, OffsetDateTime now) {
        return expireCandidates(
                occupancyJpaRepository.findStaleByUserId(userId, now, OccupancyStatus.ACTIVE), now);
    }

    @Override
    public List<ExpiredOccupancy> findStale(OffsetDateTime now) {
        return toExpired(occupancyJpaRepository.findStale(now, OccupancyStatus.ACTIVE));
    }

    /**
     * {@inheritDoc}
     *
     * <p>영향 행 수를 {@code boolean}으로 좁히는 것이 의도다. 조건부 UPDATE의 결과는
     * "내가 전이시켰는가" 하나뿐이고, 이 Method가 단건이라 1 아니면 0이다.</p>
     */
    @Override
    public boolean expire(Long occupancyId, OffsetDateTime now) {
        return occupancyJpaRepository.expireById(
                occupancyId, now, OccupancyStatus.ACTIVE, OccupancyStatus.EXPIRED) > 0;
    }

    /**
     * 후보마다 단건 조건부 전이를 시도하고, 실제로 전이된 것만 남긴다.
     *
     * <p>{@link #expire}를 그대로 재사용한다 — 스케줄러(#9)의 {@code OccupancyExpiration}이
     * 같은 방식으로 "조회 후보"와 "실제 전이 결과"를 구분하는 것과 같은 이유다.</p>
     */
    private List<ExpiredOccupancy> expireCandidates(
            List<RoomOccupancyJpaRepository.StaleOccupancyProjection> candidates, OffsetDateTime now) {
        return candidates.stream()
                .filter(candidate -> expire(candidate.getOccupancyId(), now))
                .map(candidate -> new ExpiredOccupancy(
                        candidate.getOccupancyId(),
                        candidate.getSpaceId(),
                        candidate.getEndedAt()))
                .toList();
    }

    private static List<ExpiredOccupancy> toExpired(
            List<RoomOccupancyJpaRepository.StaleOccupancyProjection> stale) {
        return stale.stream()
                .map(projection -> new ExpiredOccupancy(
                        projection.getOccupancyId(),
                        projection.getSpaceId(),
                        projection.getEndedAt()))
                .toList();
    }
}