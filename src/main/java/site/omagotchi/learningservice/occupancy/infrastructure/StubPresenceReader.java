package site.omagotchi.learningservice.occupancy.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.port.PresenceReader;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link PresenceReader}의 임시 구현.
 *
 * <p><b>이것은 재실 검증이 아니다.</b> "ACTIVE 멤버십이 있으면 재실"로 취급하는 가짜이며,
 * 출결 파트에 {@code presence_intervals}가 아직 없어서(스키마·코드 모두 부재) 점유 기능이
 * 막히지 않도록 두는 대역이다. 이 구현으로는 MR-22가 실제로 검증되지 않는다.</p>
 *
 * <p>{@code @Profile("!prod")}가 안전장치다 — 운영에서는 빈이 없어 기동이 실패하므로
 * 스텁이 조용히 배포되지 않는다 ({@code StubAccountReader}와 같은 방식).</p>
 *
 * <p><b>실제 구현이 확인해야 할 것</b>:
 * <ul>
 *   <li>열린 구간({@code ended_at IS NULL})의 {@code cohort_membership_id}를 반환</li>
 *   <li>CT-01 — 재실 판정에 {@code state = 'MEETING'}을 포함할지 (미협의)</li>
 *   <li>열린 구간이 복수일 때의 처리 — {@code uq_presence_one_open_interval}이 멤버십
 *       기준이라 다기수 담당자는 두 기수에서 동시에 열릴 수 있다 (미해결)</li>
 * </ul>
 * 아래 스텁은 세 번째 상황을 {@code requested_at} 최신 하나로 임의 처리한다.
 * 실제 규칙이 정해지면 그대로 쓰면 안 된다.</p>
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class StubPresenceReader implements PresenceReader {

    private static final String FIND_ACTIVE_MEMBERSHIP = """
            SELECT m.id
              FROM learning_service.cohort_memberships m
             WHERE m.user_id = :userId
               AND m.status = 'ACTIVE'
             ORDER BY m.requested_at DESC
             LIMIT 1
            """;

    private final EntityManager entityManager;

    @Override
    public Optional<PresenceContext> findOpenPresence(UUID userId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(FIND_ACTIVE_MEMBERSHIP)
                .setParameter("userId", userId)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PresenceContext(((Number) rows.getFirst()).longValue()));
    }
}