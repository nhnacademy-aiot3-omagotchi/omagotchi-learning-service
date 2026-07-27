package site.omagotchi.learningservice.study.infrastructure.persistence.lock;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;

@Repository
@RequiredArgsConstructor
public class PostgreSqlStudyWriteLock implements StudyWriteLock {

    private final EntityManager entityManager;

    @Override
    public void acquire(long cohortMembershipId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(StudyRecordErrorCode.WRITE_LOCK_TRANSACTION_REQUIRED);
        }

        // PostgreSQL 함수를 즉시 실행하여 먼저 락을 획득
        // 추후 다른 호출 방법이 있는지 조사 및 공부 필요
        entityManager.createNativeQuery("""
                        SELECT pg_advisory_xact_lock(CAST(:lockKey AS BIGINT))
                        """)
                .setParameter("lockKey", cohortMembershipId)
                .getSingleResult();
    }
}
