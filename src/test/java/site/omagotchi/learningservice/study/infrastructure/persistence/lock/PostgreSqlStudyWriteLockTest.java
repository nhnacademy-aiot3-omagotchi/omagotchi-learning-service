package site.omagotchi.learningservice.study.infrastructure.persistence.lock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("PostgreSQL 공부 쓰기 잠금")
class PostgreSqlStudyWriteLockTest {

    @Test
    @DisplayName("트랜잭션 외부 획득 거절")
    void rejectsAcquisitionOutsideTransaction() {
        EntityManager entityManager = mock(EntityManager.class);
        PostgreSqlStudyWriteLock studyWriteLock = new PostgreSqlStudyWriteLock(entityManager);

        assertThrows(
                IllegalStateException.class,
                () -> studyWriteLock.acquire(1L)
        );

        verifyNoInteractions(entityManager);
    }
}
