package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberCurrentStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("멤버십별 공부시간 조회")
class StudyRecordAggregationQueryServiceTest {

    private static final LocalDate START_DATE = LocalDate.parse("2000-01-03");
    private static final LocalDate END_DATE = LocalDate.parse("2000-01-07");
    private static final Duration MAX_TIMER_DURATION = Duration.ofHours(12L);

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @Mock
    private TimerRunQueryRepository timerRunQueryRepository;

    private StudyRecordAggregationQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudyRecordAggregationQueryService(
                studyRecordQueryRepository,
                timerRunQueryRepository,
                new TimerTimePolicy(MAX_TIMER_DURATION)
        );
    }

    @Nested
    @DisplayName("확정 기록 기간 합계")
    class ConfirmedDurations {

        @Test
        @DisplayName("멤버십 목록과 기간을 한 번에 위임")
        void returnsConfirmedDurations() {
            List<Long> membershipIds = List.of(10L, 20L);
            List<MemberStudyDurationResult> expected = List.of(
                    new MemberStudyDurationResult(10L, 7_200L),
                    new MemberStudyDurationResult(20L, 3_600L)
            );
            given(studyRecordQueryRepository.findConfirmedDurations(
                    membershipIds,
                    START_DATE,
                    END_DATE
            )).willReturn(expected);

            List<MemberStudyDurationResult> result = service.getConfirmedDurations(
                    membershipIds,
                    START_DATE,
                    END_DATE
            );

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("멤버십 목록이 비어 있으면 DB를 조회하지 않음")
        void skipsQueryForEmptyMemberships() {
            assertEquals(
                    List.of(),
                    service.getConfirmedDurations(List.of(), START_DATE, END_DATE)
            );

            verify(studyRecordQueryRepository, never())
                    .findConfirmedDurations(List.of(), START_DATE, END_DATE);
        }

        @Test
        @DisplayName("시작일이 종료일보다 늦으면 요청 거부")
        void rejectsInvalidDateRange() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.getConfirmedDurations(
                            List.of(10L),
                            END_DATE,
                            START_DATE
                    )
            );

            assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("현재 집계일 합계")
    class CurrentDurations {

        @Test
        @DisplayName("전날에 시작한 정상 타이머를 KST 04시부터 잘라서 반영")
        void clipsRunningTimerAtAggregationStart() {
            List<Long> membershipIds = List.of(10L, 20L);
            Set<Long> requestedMembershipIds = new LinkedHashSet<>(membershipIds);
            Instant calculatedAt = Instant.parse("2000-01-02T21:30:00Z");
            LocalDate aggregationDate = LocalDate.parse("2000-01-03");
            TimerRun previousDayTimer = TimerRun.start(
                    10L,
                    Instant.parse("2000-01-02T18:00:00Z")
            );
            TimerRun currentDayTimer = TimerRun.start(
                    20L,
                    Instant.parse("2000-01-02T20:30:00Z")
            );
            given(studyRecordQueryRepository.findConfirmedDurations(
                    requestedMembershipIds,
                    aggregationDate,
                    aggregationDate
            )).willReturn(List.of(new MemberStudyDurationResult(10L, 600L)));
            given(timerRunQueryRepository.findActiveByCohortMembershipIds(requestedMembershipIds))
                    .willReturn(List.of(previousDayTimer, currentDayTimer));

            List<MemberCurrentStudyDurationResult> result = service.getCurrentDurations(
                    membershipIds,
                    calculatedAt
            );

            assertAll(
                    () -> assertEquals(2, result.size()),
                    () -> assertEquals(9_600L, result.getFirst().studySeconds()),
                    () -> assertTrue(result.getFirst().timerRunning()),
                    () -> assertEquals(3_600L, result.getLast().studySeconds()),
                    () -> assertTrue(result.getLast().timerRunning())
            );
        }

        @Test
        @DisplayName("만료 경계의 열린 타이머는 실행 시간에서 제외")
        void excludesExpiredOpenTimer() {
            List<Long> membershipIds = List.of(10L);
            Set<Long> requestedMembershipIds = new LinkedHashSet<>(membershipIds);
            Instant calculatedAt = Instant.parse("2000-01-03T08:00:00Z");
            LocalDate aggregationDate = LocalDate.parse("2000-01-03");
            TimerRun expiredTimer = TimerRun.start(
                    10L,
                    calculatedAt.minus(MAX_TIMER_DURATION)
            );
            given(studyRecordQueryRepository.findConfirmedDurations(
                    requestedMembershipIds,
                    aggregationDate,
                    aggregationDate
            )).willReturn(List.of(new MemberStudyDurationResult(10L, 300L)));
            given(timerRunQueryRepository.findActiveByCohortMembershipIds(requestedMembershipIds))
                    .willReturn(List.of(expiredTimer));

            List<MemberCurrentStudyDurationResult> result = service.getCurrentDurations(
                    membershipIds,
                    calculatedAt
            );

            assertEquals(
                    List.of(new MemberCurrentStudyDurationResult(10L, 300L, false)),
                    result
            );
        }

        @Test
        @DisplayName("멤버십 목록이 비어 있으면 조회하지 않음")
        void skipsQueriesForEmptyMemberships() {
            List<MemberCurrentStudyDurationResult> result = service.getCurrentDurations(
                    List.of(),
                    Instant.parse("2000-01-03T08:00:00Z")
            );

            assertEquals(List.of(), result);
            verifyNoInteractions(studyRecordQueryRepository, timerRunQueryRepository);
        }
    }

    @Nested
    @DisplayName("현재 실행 타이머")
    class CurrentTimers {

        @Test
        @DisplayName("집계일 경계로 자른 시간과 원래 시작 시각 반환")
        void returnsStartedAtAndClippedAggregationDuration() {
            List<Long> membershipIds = List.of(10L, 20L);
            Set<Long> requestedMembershipIds = new LinkedHashSet<>(membershipIds);
            Instant calculatedAt = Instant.parse("2000-01-02T21:30:00Z");
            TimerRun previousDayTimer = TimerRun.start(
                    10L,
                    Instant.parse("2000-01-02T18:00:00Z")
            );
            TimerRun justStartedTimer = TimerRun.start(20L, calculatedAt);
            given(timerRunQueryRepository.findActiveByCohortMembershipIds(requestedMembershipIds))
                    .willReturn(List.of(previousDayTimer, justStartedTimer));

            List<MemberCurrentTimerResult> result = service.getCurrentTimers(
                    membershipIds,
                    calculatedAt
            );

            assertEquals(
                    List.of(
                            new MemberCurrentTimerResult(
                                    10L,
                                    Instant.parse("2000-01-02T18:00:00Z"),
                                    9_000L
                            ),
                            new MemberCurrentTimerResult(20L, calculatedAt, 0L)
                    ),
                    result
            );
        }

        @Test
        @DisplayName("만료된 열린 타이머 제외")
        void excludesExpiredOpenTimer() {
            Set<Long> membershipIds = Set.of(10L);
            Instant calculatedAt = Instant.parse("2000-01-03T08:00:00Z");
            TimerRun expiredTimer = TimerRun.start(
                    10L,
                    calculatedAt.minus(MAX_TIMER_DURATION)
            );
            given(timerRunQueryRepository.findActiveByCohortMembershipIds(membershipIds))
                    .willReturn(List.of(expiredTimer));

            List<MemberCurrentTimerResult> result = service.getCurrentTimers(
                    membershipIds,
                    calculatedAt
            );

            assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("멤버십 목록이 비어 있으면 타이머를 조회하지 않음")
        void skipsQueryForEmptyMemberships() {
            List<MemberCurrentTimerResult> result = service.getCurrentTimers(
                    List.of(),
                    Instant.parse("2000-01-03T08:00:00Z")
            );

            assertEquals(List.of(), result);
            verifyNoInteractions(timerRunQueryRepository);
        }
    }
}
