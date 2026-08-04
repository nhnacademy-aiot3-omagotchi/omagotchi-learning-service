package site.omagotchi.learningservice.occupancy.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;

import java.util.List;
import java.util.Optional;

/**
 * {@link SpaceReader}의 임시 구현.
 *
 * <p><b>TODO</b>: {@code space} 파트에 {@code SpaceAccessService}(락을 포함한 읽기 계약)를
 * 요청해둔 상태다. 도착하면 <b>이 파일의 구현만 교체</b>한다 — Port와 서비스는 그대로다.
 * 다른 파트가 소유한 테이블을 직접 읽는 것은 계층 위반이며, ArchUnit 규칙(occupancy →
 * space 내부 금지)은 그때까지 {@code @Disabled}다.
 * ({@code CohortMembershipQueryReader}가 팀 → cohort에서 쓴 것과 같은 방식이다.)</p>
 *
 * <p>JPA 엔티티가 아니라 네이티브 쿼리인 것이 의도다. {@code SpaceJpaEntity}를 읽으면
 * 그 인스턴스가 1차 캐시에 올라가고, 뒤이은 {@link #lock(Long)}이 {@code FOR UPDATE}를
 * 실제로 실행해도 Hibernate는 캐시 인스턴스를 그대로 돌려준다(재조회 결과로 필드를
 * 덮어쓰지 않는다). 그러면 락 획득 후 상태 재확인이 락 이전 스냅샷을 보게 되어
 * 비활성화 레이스 방어가 무력화된다. 값만 꺼내면 그 함정 자체가 없다.</p>
 *
 * <p>네이티브 SQL은 이 저장소에서 이미 쓰는 방식이다 —
 * {@code SpringDataSpaceRepository.existsActiveByNormalizedName},
 * {@code PostgreSqlStudyWriteLock} 참고.</p>
 */
@Repository
@RequiredArgsConstructor
public class SpaceNativeQueryReader implements SpaceReader {

    /**
     * 활성 판정을 SQL에서 계산한다. 조건절이 아니라 select 목록에 두는 것이 요점이다 —
     * WHERE에 넣으면 비활성 공간이 "행 없음"이 되어 400이어야 할 상황이 404가 된다.
     */
    private static final String SELECT_COLUMNS = """
            SELECT s.id,
                   (s.space_type = 'MEETING') AS is_meeting,
                   (s.status = 'ACTIVE' AND s.deleted_at IS NULL) AS is_active,
                   s.capacity
              FROM learning_service.spaces s
             WHERE s.id = :spaceId
            """;

    private final EntityManager entityManager;

    @Override
    public Optional<MeetingRoom> find(Long spaceId) {
        return single(entityManager.createNativeQuery(SELECT_COLUMNS), spaceId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code FOR UPDATE}가 붙는 유일한 경로다. 트랜잭션 밖에서 부르면 락이 즉시
     * 해제되어 아무것도 보장하지 못하므로, 조용히 통과시키지 않고 명시적으로 막는다
     * ({@code PostgreSqlStudyWriteLock}과 같은 방어).</p>
     */
    @Override
    public Optional<MeetingRoom> lock(Long spaceId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "spaces 행 락은 트랜잭션 안에서만 획득할 수 있습니다. spaceId=" + spaceId);
        }
        return single(entityManager.createNativeQuery(SELECT_COLUMNS + " FOR UPDATE"), spaceId);
    }

    private Optional<MeetingRoom> single(Query query, Long spaceId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.setParameter("spaceId", spaceId).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new MeetingRoom(
                ((Number) row[0]).longValue(),
                (Boolean) row[1],
                (Boolean) row[2],
                ((Number) row[3]).intValue()
        ));
    }
}