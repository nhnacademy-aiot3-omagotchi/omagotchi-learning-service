package site.omagotchi.learningservice.study.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("학습 기록")
@ExtendWith(MockitoExtension.class)
class StudyRecordCommandServiceTest {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final String DATE = "20000101";
    private static final String START_TIME_TEXT = "1000";
    private static final String END_TIME_TEXT = "1100";
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");
    private static final Instant CURRENT_TIME = Instant.parse("2000-01-02T00:00:00Z");

    @Mock
    private StudyRecordRepository studyRecordRepository;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private StudyRecordCommandService studyRecordCommandService;

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 처리")
        void savesStudyRecord() {
            CreateStudyRecordCommand request = new CreateStudyRecordCommand(
                    10L,
                    DATE,
                    START_TIME_TEXT,
                    END_TIME_TEXT
            );
            given(dateTimeProvider.currentInstant()).willReturn(END_TIME);
            given(dateTimeProvider.calculateAggregationDate(START_TIME)).willReturn(BASE_DATE);
            given(studyRecordRepository.save(any(StudyRecordEntity.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            StudyRecordResult result = studyRecordCommandService.create(
                    UUID.randomUUID(),
                    COHORT_MEMBERSHIP_ID,
                    request
            );

            ArgumentCaptor<StudyRecordEntity> captor = ArgumentCaptor.forClass(StudyRecordEntity.class);
            verify(studyRecordRepository).save(captor.capture());
            StudyRecordEntity saved = captor.getValue();

            assertAll(
                    () -> assertEquals(COHORT_MEMBERSHIP_ID, saved.getCohortMembershipId()),
                    () -> assertEquals(START_TIME, saved.getStartTime()),
                    () -> assertEquals(END_TIME, saved.getEndTime()),
                    () -> assertEquals(BASE_DATE, saved.getAggregationDate()),
                    () -> assertEquals(3_600L, saved.getStudySeconds()),
                    () -> assertEquals(saved.getStudySeconds(), result.studySeconds())
            );
        }

        @Test
        @DisplayName("시간 정책에서 계산한 집계일 적용")
        void savesAggregationDateCalculatedByDateTimeProvider() {
            Instant startTime = Instant.parse("2000-01-01T16:30:00Z");
            Instant endTime = Instant.parse("2000-01-01T17:30:00Z");
            CreateStudyRecordCommand request = new CreateStudyRecordCommand(
                    10L,
                    "20000102",
                    "0130",
                    "0230"
            );
            given(dateTimeProvider.currentInstant()).willReturn(CURRENT_TIME);
            given(dateTimeProvider.calculateAggregationDate(startTime)).willReturn(BASE_DATE);
            given(studyRecordRepository.save(any(StudyRecordEntity.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            StudyRecordResult result = studyRecordCommandService.create(
                    UUID.randomUUID(),
                    COHORT_MEMBERSHIP_ID,
                    request
            );

            assertEquals(BASE_DATE, result.aggregationDate());
        }

        @Nested
        @DisplayName("시간 및 구간 검증")
        class TimeRangeValidation {

            @Test
            @DisplayName("날짜 형식 예외")
            void rejectsInvalidDateFormat() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        "invalidDate",
                        START_TIME_TEXT,
                        END_TIME_TEXT
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("존재하지 않는 날짜 예외")
            void rejectsInvalidDateValue() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        "20000230",
                        START_TIME_TEXT,
                        END_TIME_TEXT
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("시간 형식 예외")
            void rejectsInvalidTimeFormat() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        DATE,
                        "invalid",
                        END_TIME_TEXT
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("존재하지 않는 시간 예외")
            void rejectsInvalidTimeValue() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        DATE,
                        START_TIME_TEXT,
                        "2400"
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("동일한 시작 및 종료 시각 예외")
            void rejectsEqualStartAndEndTime() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        DATE,
                        START_TIME_TEXT,
                        START_TIME_TEXT
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("시작 시각이 종료 시각 이후인 경우 예외")
            void rejectsStartTimeAfterEndTime() {
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        DATE,
                        END_TIME_TEXT,
                        START_TIME_TEXT
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("미래 종료 시각 예외")
            void rejectsFutureEndTime() {
                Instant currentTime = Instant.parse("2000-01-01T02:00:00Z");
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        10L,
                        DATE,
                        START_TIME_TEXT,
                        "1101"
                );
                given(dateTimeProvider.currentInstant()).willReturn(currentTime);

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            private BusinessException assertInvalidCreate(CreateStudyRecordCommand command) {
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> studyRecordCommandService.create(
                                UUID.randomUUID(),
                                COHORT_MEMBERSHIP_ID,
                                command
                        )
                );

                verify(studyRecordRepository, never()).save(any(StudyRecordEntity.class));
                return exception;
            }
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("정상 처리")
        void updatesExistingStudyRecord() {
            UUID studyRecordId = UUID.randomUUID();
            StudyRecordEntity entity = createEntity(START_TIME, END_TIME);
            Instant updatedStartTime = Instant.parse("2000-01-01T03:00:00Z");
            Instant updatedEndTime = Instant.parse("2000-01-01T05:00:00Z");
            UpdateStudyRecordCommand request = new UpdateStudyRecordCommand(
                    DATE,
                    "1200",
                    "1400",
                    0L
            );
            given(studyRecordRepository.findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID)).willReturn(Optional.of(entity));
            given(dateTimeProvider.currentInstant()).willReturn(CURRENT_TIME);
            given(dateTimeProvider.calculateAggregationDate(updatedStartTime)).willReturn(BASE_DATE);
            given(studyRecordRepository.save(entity)).willReturn(entity);

            StudyRecordResult result = studyRecordCommandService.update(
                    UUID.randomUUID(),
                    COHORT_MEMBERSHIP_ID,
                    studyRecordId,
                    request
            );

            assertAll(
                    () -> assertEquals(updatedStartTime, entity.getStartTime()),
                    () -> assertEquals(updatedEndTime, entity.getEndTime()),
                    () -> assertEquals(7_200L, entity.getStudySeconds()),
                    () -> assertEquals(BASE_DATE, entity.getAggregationDate()),
                    () -> assertEquals(entity.getStudySeconds(), result.studySeconds())
            );
            verify(studyRecordRepository).save(entity);
        }

        @Test
        @DisplayName("잘못된 시간 입력 예외")
        void rejectsInvalidTimeRange() {
            UUID studyRecordId = UUID.randomUUID();
            StudyRecordEntity entity = createEntity(START_TIME, END_TIME);
            UpdateStudyRecordCommand command = new UpdateStudyRecordCommand(
                    DATE,
                    END_TIME_TEXT,
                    START_TIME_TEXT,
                    0L
            );
            given(studyRecordRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            UUID.randomUUID(),
                            COHORT_MEMBERSHIP_ID,
                            studyRecordId,
                            command
                    )
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verify(studyRecordRepository, never()).save(any(StudyRecordEntity.class));
        }

        @Test
        @DisplayName("대상 없음 예외")
        void throwsNotFoundWhenUpdatingNonExistentRecord() {
            UUID studyRecordId = UUID.randomUUID();
            UpdateStudyRecordCommand request = new UpdateStudyRecordCommand(
                    DATE,
                    START_TIME_TEXT,
                    END_TIME_TEXT,
                    0L
            );
            given(studyRecordRepository.findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            UUID.randomUUID(),
                            COHORT_MEMBERSHIP_ID,
                            studyRecordId,
                            request
                    )
            );

            assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
            verify(studyRecordRepository, never()).save(any(StudyRecordEntity.class));
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("논리 삭제 처리")
        void softDeletesExistingStudyRecord() {
            UUID studyRecordId = UUID.randomUUID();
            StudyRecordEntity entity = createEntity(START_TIME, END_TIME);
            Instant deletedAt = Instant.parse("2000-01-02T01:30:00Z");
            given(studyRecordRepository.findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID)).willReturn(Optional.of(entity));
            given(dateTimeProvider.currentInstant()).willReturn(deletedAt);

            studyRecordCommandService.delete(
                    UUID.randomUUID(),
                    COHORT_MEMBERSHIP_ID,
                    studyRecordId
            );

            assertEquals(deletedAt, entity.getDeletedAt());
            verify(studyRecordRepository).save(entity);
        }

        @Test
        @DisplayName("대상 없음 예외")
        void throwsNotFoundWhenDeletingNonExistentRecord() {
            UUID studyRecordId = UUID.randomUUID();
            given(studyRecordRepository.findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID)).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.delete(
                            UUID.randomUUID(),
                            COHORT_MEMBERSHIP_ID,
                            studyRecordId
                    )
            );

            assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
            verify(studyRecordRepository, never()).save(any(StudyRecordEntity.class));
        }
    }

    private StudyRecordEntity createEntity(Instant startTime, Instant endTime) {
        return StudyRecordEntity.builder()
                .cohortMembershipId(COHORT_MEMBERSHIP_ID)
                .aggregationDate(BASE_DATE)
                .startTime(startTime)
                .endTime(endTime)
                .studySeconds(endTime.getEpochSecond() - startTime.getEpochSecond())
                .build();
    }
}
