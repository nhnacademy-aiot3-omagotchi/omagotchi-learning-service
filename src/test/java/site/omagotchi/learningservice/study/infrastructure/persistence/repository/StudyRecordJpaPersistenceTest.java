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
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.application.StudyRecordErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("JPA 학습 기록 저장소")
@ExtendWith(MockitoExtension.class)
class StudyRecordJpaPersistenceTest {

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
            UUID studyRecordId = UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );
            StudyRecord studyRecord = StudyRecord.builder()
                    .cohortMembershipId(1L)
                    .aggregationDate(LocalDate.of(2000, Month.JANUARY, 1))
                    .startTime(Instant.parse("2000-01-01T01:00:00Z"))
                    .endTime(Instant.parse("2000-01-01T02:00:00Z"))
                    .studySeconds(3_600L)
                    .build();
            given(studyRecordJpaRepository.saveAndFlush(studyRecord)).willThrow(
                    new ObjectOptimisticLockingFailureException(StudyRecord.class, studyRecordId)
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
