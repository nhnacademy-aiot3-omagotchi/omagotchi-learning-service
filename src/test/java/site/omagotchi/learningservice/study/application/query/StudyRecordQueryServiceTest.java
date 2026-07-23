package site.omagotchi.learningservice.study.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.exception.StudyRecordErrorCode;
import site.omagotchi.learningservice.study.infrastructure.persistence.entity.StudyRecordEntity;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("학습 기록 조회")
@ExtendWith(MockitoExtension.class)
class StudyRecordQueryServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");

    @Mock
    private StudyRecordRepository studyRecordRepository;

    @InjectMocks
    private StudyRecordQueryService studyRecordQueryService;

    @Test
    @DisplayName("정상 처리")
    void returnsStudyRecordResult() {
        UUID studyRecordId = UUID.randomUUID();
        StudyRecordEntity entity = StudyRecordEntity.builder()
                .cohortMembershipId(1L)
                .aggregationDate(BASE_DATE)
                .startTime(START_TIME)
                .endTime(END_TIME)
                .studySeconds(3_600L)
                .build();
        given(studyRecordRepository.findByIdAndCohortMembershipIdAndDeletedAtIsNull(studyRecordId, 1L))
                .willReturn(Optional.of(entity));

        StudyRecordResult result = studyRecordQueryService.getRecord(1L, studyRecordId);

        assertAll(
                () -> assertEquals(entity.getId(), result.id()),
                () -> assertEquals(BASE_DATE, result.aggregationDate()),
                () -> assertEquals(START_TIME, result.startTime()),
                () -> assertEquals(END_TIME, result.endTime()),
                () -> assertEquals(3_600L, result.studySeconds())
        );
        verify(studyRecordRepository)
                .findByIdAndCohortMembershipIdAndDeletedAtIsNull(studyRecordId, 1L);
        verifyNoMoreInteractions(studyRecordRepository);
    }

    @Test
    @DisplayName("대상 없음 예외")
    void throwsNotFoundWhenRecordDoesNotExist() {
        UUID studyRecordId = UUID.randomUUID();
        given(studyRecordRepository.findByIdAndCohortMembershipIdAndDeletedAtIsNull(studyRecordId, 1L))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyRecordQueryService.getRecord(1L, studyRecordId)
        );

        assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
        verify(studyRecordRepository)
                .findByIdAndCohortMembershipIdAndDeletedAtIsNull(studyRecordId, 1L);
        verifyNoMoreInteractions(studyRecordRepository);
    }
}
