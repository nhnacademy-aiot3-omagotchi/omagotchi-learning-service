package site.omagotchi.learningservice.cohort.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.cohort.application.port.CohortManagerAssignmentLock;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgreSqlCohortManagerAssignmentLock implements CohortManagerAssignmentLock {

    private static final int COHORT_LOCK_NAMESPACE = 0x434F484F; // "COHO"
    private static final int USER_LOCK_NAMESPACE = 0x55534552; // "USER"

    private final EntityManager entityManager;

    @Override
    public void acquireCohort(Long cohortId) {
        acquire(COHORT_LOCK_NAMESPACE, cohortId.toString());
    }

    @Override
    public void acquireUser(UUID userId) {
        acquire(USER_LOCK_NAMESPACE, userId.toString());
    }

    private void acquire(int namespace, String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("기수 관리자 배치 잠금을 획득하려면 활성 트랜잭션이 필요합니다.");
        }

        entityManager.createNativeQuery("""
                        SELECT pg_advisory_xact_lock(
                            CAST(:namespace AS INTEGER),
                            hashtext(CAST(:key AS TEXT))
                        )
                        """)
                .setParameter("namespace", namespace)
                .setParameter("key", key)
                .getSingleResult();
    }
}
