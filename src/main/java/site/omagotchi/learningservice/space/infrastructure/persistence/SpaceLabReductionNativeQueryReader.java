package site.omagotchi.learningservice.space.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.space.application.port.SpaceLabReductionQueryPort;
import site.omagotchi.learningservice.space.application.result.SpaceLabReductionView;

import java.util.List;
import java.util.Optional;

/**
 * 공간 엔티티를 1차 캐시에 올리지 않고 기수 선행 잠금에 필요한 값만 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class SpaceLabReductionNativeQueryReader implements SpaceLabReductionQueryPort {

    private final EntityManager entityManager;

    @Override
    public Optional<SpaceLabReductionView> find(Long spaceId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT space.id,
                               space.cohort_id,
                               (space.space_type = 'LAB' AND space.status = 'ACTIVE') AS active_lab
                          FROM learning_service.spaces space
                         WHERE space.id = :spaceId
                           AND space.deleted_at IS NULL
                        """)
                .setParameter("spaceId", spaceId)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new SpaceLabReductionView(
                ((Number) row[0]).longValue(),
                row[1] == null ? null : ((Number) row[1]).longValue(),
                (Boolean) row[2]
        ));
    }
}
