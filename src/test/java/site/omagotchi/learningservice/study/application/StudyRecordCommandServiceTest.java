package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("학습 기록")
@ExtendWith(MockitoExtension.class)
class StudyRecordCommandServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");
    private static final Instant CURRENT_TIME = Instant.parse("2000-01-02T00:00:00Z");

    @Mock
    private StudyRecordRepository studyRecordRepository;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @Mock
    private site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository timerRunQueryRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private Clock clock;

    @Mock
    private StudyWriteLock studyWriteLock;

    @InjectMocks
    private StudyRecordCommandService studyRecordCommandService;

    @Nested
    @DisplayName("공부 기록 생성")
    class Create {

        @Test
        @DisplayName("저장")
        void savesStudyRecord() {
            givenActiveMembership();
            CreateStudyRecordCommand request = new CreateStudyRecordCommand(
                    START_TIME,
                    END_TIME
            );
            given(clock.instant()).willReturn(CURRENT_TIME);
            given(studyRecordRepository.save(any(StudyRecord.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            StudyRecordResult result = studyRecordCommandService.create(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    request
            );

            // advisory lock에 의한 순서 보장 검증 (잠금 -> 조회 -> 저장)
            InOrder inOrder = inOrder(studyWriteLock, studyRecordQueryRepository, studyRecordRepository);
            inOrder.verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
            inOrder.verify(studyRecordQueryRepository).existsActiveOverlap(
                    eq(COHORT_MEMBERSHIP_ID),
                    eq(START_TIME),
                    eq(END_TIME),
                    isNull()
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            inOrder.verify(studyRecordRepository).save(captor.capture());
            StudyRecord saved = captor.getValue();

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
        @DisplayName("활성 소속 없음 예외")
        void doesNotCreateRecordWhenActiveMembershipDoesNotExist() {
            CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                    START_TIME,
                    END_TIME
            );
            given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                    .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.create(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            command
                    )
            );

            assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
            verifyNoInteractions(
                    studyRecordRepository,
                    studyRecordQueryRepository,
                    studyWriteLock
            );
        }

        @Test
        @DisplayName("시간 정책에서 계산한 집계일 적용")
        void savesAggregationDateCalculatedByStudyTimePolicy() {
            givenActiveMembership();
            Instant startTime = Instant.parse("2000-01-01T16:30:00Z");
            Instant endTime = Instant.parse("2000-01-01T17:30:00Z");
            CreateStudyRecordCommand request = new CreateStudyRecordCommand(
                    startTime,
                    endTime
            );
            given(clock.instant()).willReturn(CURRENT_TIME);
            given(studyRecordRepository.save(any(StudyRecord.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            StudyRecordResult result = studyRecordCommandService.create(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    request
            );

            assertEquals(BASE_DATE, result.aggregationDate());
        }

        @Test
        @DisplayName("기존 기록 겹침 예외")
        void throwsOverlapWhenCreatingOverlappingRecord() {
            givenActiveMembership();
            CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                    START_TIME,
                    END_TIME
            );
            given(clock.instant()).willReturn(END_TIME);
            given(studyRecordQueryRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    START_TIME,
                    END_TIME,
                    null
            )).willReturn(true);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.create(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            command
                    )
            );

            assertSame(StudyRecordErrorCode.OVERLAP, exception.getErrorCode());
            verify(studyRecordRepository, never()).save(any(StudyRecord.class));
        }

        @Nested
        @DisplayName("시간 및 구간 검증")
        class TimeRangeValidation {

            @Test
            @DisplayName("동일한 시작 및 종료 시각 예외")
            void rejectsEqualStartAndEndTime() {
            givenActiveMembership();
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        START_TIME,
                        START_TIME
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }


            @Test
            @DisplayName("시작 시각이 종료 시각 이후인 경우 예외")
            void rejectsStartTimeAfterEndTime() {
            givenActiveMembership();
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        END_TIME,
                        START_TIME
                );

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("미래 종료 시각 예외")
            void rejectsFutureEndTime() {
            givenActiveMembership();
                Instant currentTime = Instant.parse("2000-01-01T02:00:00Z");
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        START_TIME,
                        currentTime.plusSeconds(60)
                );
                given(clock.instant()).willReturn(currentTime);

                BusinessException exception = assertInvalidCreate(command);

                assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            }

            @Test
            @DisplayName("04시 집계 경계 교차 예외")
            void rejectsAggregationBoundaryCrossing() {
            givenActiveMembership();
                Instant startTime = Instant.parse("1999-12-31T18:59:00Z");
                Instant endTime = Instant.parse("1999-12-31T19:01:00Z");
                CreateStudyRecordCommand command = new CreateStudyRecordCommand(
                        startTime,
                        endTime
                );
                given(clock.instant()).willReturn(CURRENT_TIME);

                BusinessException exception = assertInvalidCreate(command);

                assertSame(
                        StudyRecordErrorCode.AGGREGATION_BOUNDARY_CROSSED,
                        exception.getErrorCode()
                );
            }

            private BusinessException assertInvalidCreate(CreateStudyRecordCommand command) {
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> studyRecordCommandService.create(
                                COMMAND_ID,
                                USER_ID,
                                COHORT_ID,
                                command
                        )
                );

                verify(studyRecordRepository, never()).save(any(StudyRecord.class));
                return exception;
            }
        }
    }

    @Nested
    @DisplayName("공부 기록 수정")
    class Update {

        @Test
        @DisplayName("기록 수정")
        void updatesExistingStudyRecord() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            Instant updatedStartTime = Instant.parse("2000-01-01T03:00:00Z");
            Instant updatedEndTime = Instant.parse("2000-01-01T05:00:00Z");
            Instant expectedStartTime = Instant.parse("2000-01-01T03:00:00Z");
            Instant expectedEndTime = Instant.parse("2000-01-01T05:00:00Z");
            UpdateStudyRecordCommand request = new UpdateStudyRecordCommand(
                    updatedStartTime,
                    updatedEndTime,
                    0L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));
            given(clock.instant()).willReturn(CURRENT_TIME);
            given(studyRecordRepository.saveWithVersionCheck(entity)).willReturn(entity);

            StudyRecordResult result = studyRecordCommandService.update(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    studyRecordId,
                    request
            );

            // advisory lock에 의한 순서 보장 검증 (잠금 -> 조회 -> 저장)
            InOrder inOrder = inOrder(studyWriteLock, studyRecordQueryRepository, studyRecordRepository);
            inOrder.verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
            inOrder.verify(studyRecordQueryRepository).findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID);
            inOrder.verify(studyRecordRepository).saveWithVersionCheck(entity);

            assertAll(
                    () -> assertEquals(expectedStartTime, entity.getStartTime()),
                    () -> assertEquals(expectedEndTime, entity.getEndTime()),
                    () -> assertEquals(7_200L, entity.getStudySeconds()),
                    () -> assertEquals(BASE_DATE, entity.getAggregationDate()),
                    () -> assertEquals(entity.getStudySeconds(), result.studySeconds())
            );
        }

        @Test
        @DisplayName("잘못된 시간 입력 예외")
        void rejectsInvalidTimeRange() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            UpdateStudyRecordCommand command = new UpdateStudyRecordCommand(
                    END_TIME,
                    START_TIME,
                    0L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            command
                    )
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }

        @Test
        @DisplayName("기존 기록 겹침 예외")
        void throwsOverlapWhenUpdatingToOverlappingRecord() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            Instant updatedStartTime = Instant.parse("2000-01-01T03:00:00Z");
            Instant updatedEndTime = Instant.parse("2000-01-01T05:00:00Z");
            UpdateStudyRecordCommand command = new UpdateStudyRecordCommand(
                    updatedStartTime,
                    updatedEndTime,
                    0L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));
            given(clock.instant()).willReturn(CURRENT_TIME);
            given(studyRecordQueryRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    updatedStartTime,
                    updatedEndTime,
                    studyRecordId
            )).willReturn(true);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            command
                    )
            );

            assertAll(
                    () -> assertSame(StudyRecordErrorCode.OVERLAP, exception.getErrorCode()),
                    () -> assertEquals(START_TIME, entity.getStartTime()),
                    () -> assertEquals(END_TIME, entity.getEndTime())
            );
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }

        @Test
        @DisplayName("기대 버전 불일치 예외")
        void throwsVersionConflictWhenExpectedVersionDoesNotMatch() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            UpdateStudyRecordCommand command = new UpdateStudyRecordCommand(
                    Instant.parse("2000-01-01T03:00:00Z"),
                    Instant.parse("2000-01-01T05:00:00Z"),
                    1L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            command
                    )
            );

            assertAll(
                    () -> assertSame(StudyRecordErrorCode.VERSION_CONFLICT, exception.getErrorCode()),
                    () -> assertEquals(START_TIME, entity.getStartTime()),
                    () -> assertEquals(END_TIME, entity.getEndTime())
            );
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }

        @Test
        @DisplayName("04시 집계 경계 교차 예외")
        void rejectsAggregationBoundaryCrossing() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            Instant updatedStartTime = Instant.parse("1999-12-31T18:59:00Z");
            Instant updatedEndTime = Instant.parse("1999-12-31T19:01:00Z");
            UpdateStudyRecordCommand command = new UpdateStudyRecordCommand(
                    updatedStartTime,
                    updatedEndTime,
                    0L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));
            given(clock.instant()).willReturn(CURRENT_TIME);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            command
                    )
            );

            assertAll(
                    () -> assertSame(
                            StudyRecordErrorCode.AGGREGATION_BOUNDARY_CROSSED,
                            exception.getErrorCode()
                    ),
                    () -> assertEquals(START_TIME, entity.getStartTime()),
                    () -> assertEquals(END_TIME, entity.getEndTime())
            );
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }

        @Test
        @DisplayName("대상 없음 예외")
        void throwsNotFoundWhenUpdatingNonExistentRecord() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            UpdateStudyRecordCommand request = new UpdateStudyRecordCommand(
                    START_TIME,
                    END_TIME,
                    0L
            );
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.update(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            request
                    )
            );

            assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }
    }

    @Nested
    @DisplayName("공부 기록 삭제")
    class Delete {

        @Test
        @DisplayName("논리 삭제 처리")
        void softDeletesExistingStudyRecord() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            Instant deletedAt = Instant.parse("2000-01-02T01:30:00Z");
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));
            given(clock.instant()).willReturn(deletedAt);

            studyRecordCommandService.delete(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    studyRecordId,
                    0L
            );

            assertEquals(deletedAt, entity.getDeletedAt());
            // advisory lock에 의한 순서 보장 검증 (잠금 -> 조회 -> 저장)
            InOrder inOrder = inOrder(studyWriteLock, studyRecordQueryRepository, studyRecordRepository);
            inOrder.verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
            inOrder.verify(studyRecordQueryRepository).findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID);
            inOrder.verify(studyRecordRepository).saveWithVersionCheck(entity);
        }

        @Test
        @DisplayName("기대 버전 불일치 예외")
        void throwsVersionConflictWhenDeletingWithStaleVersion() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            StudyRecord entity = createEntity(START_TIME, END_TIME);
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(entity));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.delete(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            1L
                    )
            );

            assertAll(
                    () -> assertSame(StudyRecordErrorCode.VERSION_CONFLICT, exception.getErrorCode()),
                    () -> assertNull(entity.getDeletedAt())
            );
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }

        @Test
        @DisplayName("대상 없음 예외")
        void throwsNotFoundWhenDeletingNonExistentRecord() {
            givenActiveMembership();
            UUID studyRecordId = STUDY_RECORD_ID;
            given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                    studyRecordId,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRecordCommandService.delete(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            studyRecordId,
                            0L
                    )
            );

            assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
            verify(studyRecordRepository, never()).saveWithVersionCheck(any(StudyRecord.class));
        }
    }

    // ===== Private Methods =====

    private void givenActiveMembership() {
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    private StudyRecord createEntity(Instant startTime, Instant endTime) {
        StudyRecord entity = StudyRecord.create(
                COHORT_MEMBERSHIP_ID,
                startTime,
                endTime,
                endTime.getEpochSecond() - startTime.getEpochSecond()
        );

        ReflectionTestUtils.setField(entity, "id", STUDY_RECORD_ID);
        ReflectionTestUtils.setField(entity, "version", 0L);
        return entity;
    }
}
