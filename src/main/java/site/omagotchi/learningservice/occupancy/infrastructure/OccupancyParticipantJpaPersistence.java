package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public Optional<OccupancyParticipant> findByOccupancyIdAndUserId(Long occupancyId, UUID userId) {
        return participantJpaRepository.findByOccupancyIdAndUserId(occupancyId, userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code Collectors.groupingBy}가 아니라 {@link LinkedHashMap}에 직접 넣는 것은
     * 값 목록의 순서를 보장하기 위해서다. 쿼리가 {@code id} 오름차순으로 정렬해도
     * 그룹핑 구현이 순서를 지킨다는 보장이 계약에 없다.</p>
     */
    @Override
    public Map<Long, List<UUID>> findActiveUserIdsByOccupancyIds(Collection<Long> occupancyIds) {
        if (occupancyIds == null || occupancyIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<UUID>> userIdsByOccupancyId = new LinkedHashMap<>();
        participantJpaRepository.findActiveByOccupancyIds(occupancyIds)
                .forEach(participant -> userIdsByOccupancyId
                        .computeIfAbsent(participant.getOccupancyId(), key -> new ArrayList<>())
                        .add(participant.getUserId()));
        return userIdsByOccupancyId;
    }

    @Override
    public List<OpenParticipation> findOpenParticipationsAfter(Long afterId, int limit) {
        return participantJpaRepository.findOpenParticipationsAfter(
                afterId, PageRequest.of(0, limit));
    }

    @Override
    public int closeActiveByUserId(UUID userId, OffsetDateTime endedAt) {
        return participantJpaRepository.closeActiveByUserId(userId, endedAt);
    }

    @Override
    public int closeAllActiveByOccupancyId(Long occupancyId, OffsetDateTime endedAt) {
        return participantJpaRepository.closeAllActiveByOccupancyId(occupancyId, endedAt);
    }
}
