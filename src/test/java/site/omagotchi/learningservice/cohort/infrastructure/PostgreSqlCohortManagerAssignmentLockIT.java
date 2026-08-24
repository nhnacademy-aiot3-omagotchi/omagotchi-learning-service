package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.port.CohortManagerAssignmentLock;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("기수 관리자 배치 PostgreSQL 잠금")
class PostgreSqlCohortManagerAssignmentLockIT {

    private static final Long COHORT_ID = 7L;
    private static final UUID USER_ID = new UUID(0L, 10L);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private CohortManagerAssignmentLock assignmentLock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 기수 잠금은 선행 트랜잭션 커밋까지 대기")
    void releasesCohortLockAfterCommit() throws Exception {
        verifiesReleaseAfterTransaction(
                () -> assignmentLock.acquireCohort(COHORT_ID),
                false
        );
    }

    @Test
    @DisplayName("같은 사용자 잠금은 선행 트랜잭션 롤백 뒤 해제")
    void releasesUserLockAfterRollback() throws Exception {
        verifiesReleaseAfterTransaction(
                () -> assignmentLock.acquireUser(USER_ID),
                true
        );
    }

    private void verifiesReleaseAfterTransaction(LockAction lockAction, boolean rollback) throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);

        Future<?> firstTransaction = executor.submit(() -> inTransaction(() -> {
            lockAction.acquire();
            firstLockAcquired.countDown();
            await(releaseFirstTransaction);
        }, rollback));
        assertTrue(firstLockAcquired.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        Future<?> secondTransaction = executor.submit(() -> inTransaction(() -> {
            secondTransactionStarted.countDown();
            lockAction.acquire();
        }, false));
        assertTrue(secondTransactionStarted.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        assertThrows(TimeoutException.class, () -> secondTransaction.get(300, TimeUnit.MILLISECONDS));

        releaseFirstTransaction.countDown();
        firstTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        secondTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void inTransaction(Runnable action, boolean rollback) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            action.run();
            if (rollback) {
                status.setRollbackOnly();
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface LockAction {
        void acquire();
    }
}
