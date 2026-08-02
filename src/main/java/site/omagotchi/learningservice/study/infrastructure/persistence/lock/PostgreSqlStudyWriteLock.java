package site.omagotchi.learningservice.study.infrastructure.persistence.lock;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;

import java.sql.SQLException;

@Repository
@RequiredArgsConstructor
public class PostgreSqlStudyWriteLock implements StudyWriteLock {

    private static final String LOCK_TIMEOUT = "1000ms";
    private static final String LOCK_TIMEOUT_SQL_STATE = "55P03";

    private final EntityManager entityManager;

    @Override
    public void acquire(long cohortMembershipId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "공부 쓰기 잠금을 획득하려면 활성 트랜잭션이 필요합니다."
            );
        }

        try {
            entityManager.createNativeQuery("""
                            WITH lock_timeout_config AS MATERIALIZED (
                                SELECT set_config('lock_timeout', :lockTimeout, true)
                            )
                            SELECT pg_advisory_xact_lock(
                                CAST(:cohortMembershipId AS BIGINT)
                            )
                            FROM lock_timeout_config
                            """)
                    .setParameter("lockTimeout", LOCK_TIMEOUT)
                    .setParameter("cohortMembershipId", cohortMembershipId)
                    .getSingleResult();
        } catch (RuntimeException exception) {
            if (isLockTimeout(exception)) {
                throw new BusinessException(StudyRecordErrorCode.WRITE_LOCK_TIMEOUT);
            }
            throw exception;
        }
    }

    private boolean isLockTimeout(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && LOCK_TIMEOUT_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
