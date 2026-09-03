package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * periodDays는 LLM이 채우는 값이라 서버가 확정·검증한다
 * 지표 계산은 KST 04:00 집계일을 원점으로 하므로 새벽 시간대가 전날에 귀속되는지도 함께 고정한다
 * (ADR ai-assistant/0010 LLM이 채운 Tool 인자를 신뢰하지 않고 서버가 확정)
 */
@DisplayName("본인 학습 패턴 조회")
@ExtendWith(MockitoExtension.class)
class StudyPatternQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long MEMBERSHIP_ID = 42L;

    // 집계일이 2026-03-10이 되도록 고정한다 (KST 14:00)
    private static final Instant NOW = Instant.parse("2026-03-10T05:00:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    private StudyPatternQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudyPatternQueryService(
                cohortAccessService,
                studyRecordQueryRepository,
                Clock.fixed(NOW, KST)
        );
    }

    @Nested
    @DisplayName("조회 기간 확정")
    class PeriodResolution {

        @Test
        @DisplayName("기간을 지정하지 않으면 기본값 30일로 조회한다")
        void usesDefaultThirtyDaysWhenNotSpecified() {
            givenActiveMembership();
            givenRecords();

            StudyPatternResult result = service.getPattern(USER_ID, null);

            assertThat(result.periodDays()).isEqualTo(30);
            // 2026-03-10 집계일 기준 30일이면 시작일은 2026-02-09다
            verify(studyRecordQueryRepository).findActiveRecordsBetween(
                    MEMBERSHIP_ID, LocalDate.parse("2026-02-09"), LocalDate.parse("2026-03-10"));
        }

        @ParameterizedTest(name = "{0}일")
        @ValueSource(ints = {1, 30, 90})
        @DisplayName("허용 범위(1~90일) 안의 값은 그대로 사용한다")
        void acceptsPeriodWithinAllowedRange(int periodDays) {
            givenActiveMembership();
            givenRecords();

            StudyPatternResult result = service.getPattern(USER_ID, periodDays);

            assertThat(result.periodDays()).isEqualTo(periodDays);
        }

        @ParameterizedTest(name = "{0}일")
        @ValueSource(ints = {0, -1, 91, 365})
        @DisplayName("허용 범위를 벗어나면 거부하고 조회하지 않는다")
        void rejectsPeriodOutOfRange(int periodDays) {
            assertThatThrownBy(() -> service.getPattern(USER_ID, periodDays))
                    .isInstanceOf(BusinessException.class);

            // 범위 검증이 먼저라 소속 조회·기록 조회로 넘어가지 않아야 한다
            verify(cohortAccessService, never()).requireCurrentActiveMembership(any());
            verify(studyRecordQueryRepository, never()).findActiveRecordsBetween(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("기록이 없을 때")
    class NoData {

        @Test
        @DisplayName("기간 내 기록이 없으면 NO_DATA를 돌려주고 지표는 비운다")
        void returnsNoDataWithEmptyMetrics() {
            givenActiveMembership();
            givenRecords();

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.status()).isEqualTo(StudyPatternResult.Status.NO_DATA);
            assertThat(result.periodDays()).isEqualTo(30);
            assertThat(result.studyDayCount()).isZero();
            assertThat(result.totalStudyMinutes()).isZero();
            assertThat(result.typicalStartTime()).isNull();
            assertThat(result.bestStartHour()).isNull();
            assertThat(result.focusDensityPercent()).isZero();
            assertThat(result.currentStreakDays()).isZero();
        }
    }

    @Nested
    @DisplayName("지표 계산")
    class Metrics {

        @Test
        @DisplayName("총 공부 시간·세션 수·평균/최장 세션을 집계한다")
        void aggregatesDurationMetrics() {
            givenActiveMembership();
            // 09:00 KST에 30분, 14:00 KST에 90분
            givenRecords(
                    record("2026-03-09T00:00:00Z", 1800),
                    record("2026-03-09T05:00:00Z", 5400)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.status()).isEqualTo(StudyPatternResult.Status.OK);
            assertThat(result.sessionCount()).isEqualTo(2);
            assertThat(result.totalStudyMinutes()).isEqualTo(120);
            assertThat(result.averageSessionMinutes()).isEqualTo(60);
            assertThat(result.longestSessionMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("몰입 밀도는 앉아 있던 시간 대비 실제 공부 시간의 비율이다")
        void calculatesFocusDensityFromOccupiedTime() {
            givenActiveMembership();
            // 2시간 앉아 1시간만 공부한 세션 → 50%
            Instant start = Instant.parse("2026-03-09T00:00:00Z");
            given(studyRecordQueryRepository.findActiveRecordsBetween(any(), any(), any()))
                    .willReturn(List.of(StudyRecord.create(
                            MEMBERSHIP_ID, start, start.plusSeconds(7200), 3600)));

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.focusDensityPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("누적 공부 시간이 가장 많은 시작 시각대를 bestStartHour로 고른다")
        void picksStartHourWithMostAccumulatedStudyTime() {
            givenActiveMembership();
            // 09시 시작 30분 + 09시 시작 30분 = 60분, 14시 시작 50분
            givenRecords(
                    record("2026-03-08T00:00:00Z", 1800),
                    record("2026-03-09T00:00:00Z", 1800),
                    record("2026-03-09T05:00:00Z", 3000)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.bestStartHour()).isEqualTo(9);
        }

        @Test
        @DisplayName("여러 날 기록이면 날짜별 첫 세션 시작 시각의 중앙값을 대표 시각으로 쓴다")
        void usesMedianOfDailyFirstSessionStartTimes() {
            givenActiveMembership();
            // 3/7 08:00, 3/8 09:00, 3/9 13:00 시작 → 중앙값 09:00
            givenRecords(
                    record("2026-03-06T23:00:00Z", 1800),
                    record("2026-03-08T00:00:00Z", 1800),
                    record("2026-03-09T04:00:00Z", 1800)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.typicalStartTime()).isEqualTo("09:00");
        }
    }

    @Nested
    @DisplayName("연속 학습일")
    class Streak {

        @Test
        @DisplayName("오늘 기록이 있으면 오늘부터 거꾸로 센다")
        void countsFromTodayWhenTodayHasRecord() {
            givenActiveMembership();
            // 3/8, 3/9, 3/10(오늘) 연속
            givenRecords(
                    record("2026-03-08T01:00:00Z", 1800),
                    record("2026-03-09T01:00:00Z", 1800),
                    record("2026-03-10T01:00:00Z", 1800)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.currentStreakDays()).isEqualTo(3);
        }

        @Test
        @DisplayName("오늘 기록이 없으면 어제부터 거꾸로 세어 연속을 끊지 않는다")
        void countsFromYesterdayWhenTodayHasNoRecord() {
            givenActiveMembership();
            // 3/8, 3/9까지만 (오늘 3/10은 아직 공부 전)
            givenRecords(
                    record("2026-03-08T01:00:00Z", 1800),
                    record("2026-03-09T01:00:00Z", 1800)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.currentStreakDays()).isEqualTo(2);
        }

        @Test
        @DisplayName("중간에 빠진 날이 있으면 거기서 끊는다")
        void stopsAtTheFirstMissingDay() {
            givenActiveMembership();
            // 3/6, 3/7 ... (3/8 빠짐) ... 3/9, 3/10
            givenRecords(
                    record("2026-03-06T01:00:00Z", 1800),
                    record("2026-03-07T01:00:00Z", 1800),
                    record("2026-03-09T01:00:00Z", 1800),
                    record("2026-03-10T01:00:00Z", 1800)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.currentStreakDays()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("KST 04:00 집계일 경계")
    class AggregationDateBoundary {

        @Test
        @DisplayName("새벽 0~4시 학습은 전날 집계일로 묶여 하루로 센다")
        void treatsEarlyMorningAsPreviousAggregationDate() {
            givenActiveMembership();
            // 3/9 22:00 KST 시작과 3/10 02:00 KST 시작 → 둘 다 3/9 집계일
            givenRecords(
                    record("2026-03-09T13:00:00Z", 1800),
                    record("2026-03-09T17:00:00Z", 1800)
            );

            StudyPatternResult result = service.getPattern(USER_ID, 30);

            assertThat(result.studyDayCount()).isEqualTo(1);
            assertThat(result.sessionCount()).isEqualTo(2);
        }
    }

    private void givenActiveMembership() {
        CohortMembership membership = mock(CohortMembership.class);
        given(membership.getId()).willReturn(MEMBERSHIP_ID);
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(membership);
    }

    private void givenRecords(StudyRecord... records) {
        given(studyRecordQueryRepository.findActiveRecordsBetween(eq(MEMBERSHIP_ID), any(), any()))
                .willReturn(List.of(records));
    }

    /** 시작 시각과 공부 시간이 같은(=끊김 없는) 기록. 몰입 밀도는 100%가 된다. */
    private StudyRecord record(String startTime, long studySeconds) {
        Instant start = Instant.parse(startTime);
        return StudyRecord.create(MEMBERSHIP_ID, start, start.plusSeconds(studySeconds), studySeconds);
    }
}
