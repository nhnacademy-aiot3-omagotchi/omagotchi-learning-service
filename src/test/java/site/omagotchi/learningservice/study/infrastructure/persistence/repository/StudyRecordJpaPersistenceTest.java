package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("JPA 학습 기록 저장소")
@ExtendWith(MockitoExtension.class)
class StudyRecordJpaPersistenceTest {

    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );

    @Mock
    private StudyRecordJpaRepository studyRecordJpaRepository;

    @InjectMocks
    private StudyRecordJpaPersistence studyRecordJpaPersistence;

    @Nested
    @DisplayName("버전 검증 저장")
    class SaveWithVersionCheck {

        @Test
        @DisplayName("동시 변경 충돌 예외")
        void translatesOptimisticLockingFailureToVersionConflict() {
            StudyRecord studyRecord = StudyRecord.create(
                    1L,
                    Instant.parse("2000-01-01T01:00:00Z"),
                    Instant.parse("2000-01-01T02:00:00Z"),
                    3_600L
            );
            given(studyRecordJpaRepository.saveAndFlush(studyRecord)).willThrow(
                    new ObjectOptimisticLockingFailureException(StudyRecord.class, STUDY_RECORD_ID)
            );

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordJpaPersistence.saveWithVersionCheck(studyRecord)
            );

            assertSame(StudyRecordErrorCode.VERSION_CONFLICT, exception.getErrorCode());
            verify(studyRecordJpaRepository).saveAndFlush(studyRecord);
        }
    }
}
