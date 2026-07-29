package site.omagotchi.learningservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;

import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("공부 쓰기 잠금")
class PostgreSqlStudyWriteLockIT {

    private static final long COHORT_MEMBERSHIP_ID = 1L;
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LOCK_TIMEOUT_ASSERTION_LIMIT = Duration.ofSeconds(2);

    @Autowired
    private StudyWriteLock studyWriteLock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 소속 쓰기 직렬화")
    void serializesWritesForSameCohortMembership() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);

        Future<?> firstTransaction = executor.submit(() -> inTransaction(() -> {
            studyWriteLock.acquire(COHORT_MEMBERSHIP_ID);
            firstLockAcquired.countDown();
            await(releaseFirstTransaction);
        }));
        assertTrue(firstLockAcquired.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        Future<?> secondTransaction = executor.submit(() -> inTransaction(() -> {
            secondTransactionStarted.countDown();
            studyWriteLock.acquire(COHORT_MEMBERSHIP_ID);
        }));
        assertTrue(secondTransactionStarted.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        assertThrows(
                TimeoutException.class,
                () -> secondTransaction.get(300, TimeUnit.MILLISECONDS)
        );

        releaseFirstTransaction.countDown();

        firstTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        secondTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("다른 소속 쓰기 독립 처리")
    void allowsWritesForDifferentCohortMemberships() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        Future<?> firstTransaction = executor.submit(() -> inTransaction(() -> {
            studyWriteLock.acquire(COHORT_MEMBERSHIP_ID);
            firstLockAcquired.countDown();
            await(releaseFirstTransaction);
        }));
        assertTrue(firstLockAcquired.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        Future<?> secondTransaction = executor.submit(() -> inTransaction(
                () -> studyWriteLock.acquire(COHORT_MEMBERSHIP_ID + 1)
        ));

        secondTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        releaseFirstTransaction.countDown();
        firstTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("같은 소속 잠금 대기 시간 초과")
    void timesOutWhileWaitingForSameCohortMembership() throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        Future<?> firstTransaction = executor.submit(() -> inTransaction(() -> {
            studyWriteLock.acquire(COHORT_MEMBERSHIP_ID);
            firstLockAcquired.countDown();
            await(releaseFirstTransaction);
        }));
        assertTrue(firstLockAcquired.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

        Future<BusinessException> secondTransaction = executor.submit(() -> assertThrows(
                BusinessException.class,
                () -> inTransaction(() -> studyWriteLock.acquire(COHORT_MEMBERSHIP_ID))
        ));

        try {
            BusinessException exception = secondTransaction.get(
                    LOCK_TIMEOUT_ASSERTION_LIMIT.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            assertEquals(StudyRecordErrorCode.WRITE_LOCK_TIMEOUT, exception.getErrorCode());
        } finally {
            releaseFirstTransaction.countDown();
            firstTransaction.get(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
