package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 리포트는 "이번 기간"과 "직전 같은 길이 구간"을 나란히 놓아 변화를 말한다
 * 두 구간이 겹치거나 하루씩 어긋나면 "지난주 대비" 문장이 조용히 틀린 값을 말하게 되므로
 * 경계 계산을 날짜로 못박아 둔다
 */
@DisplayName("기간 학습 리포트 조회")
@ExtendWith(MockitoExtension.class)
class LearningReportQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long MEMBERSHIP_ID = 42L;

    // 집계일이 2026-03-10이 되도록 고정한다
    private static final Instant NOW = Instant.parse("2026-03-10T05:00:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private TopLearnerPatternQueryService topLearnerPatternQueryService;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    private LearningReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new LearningReportQueryService(
                topLearnerPatternQueryService,
                cohortAccessService,
                studyRecordQueryRepository,
                Clock.fixed(NOW, KST)
        );
    }

    @Nested
    @DisplayName("기간 확정")
    class PeriodResolution {

        @Test
        @DisplayName("기간을 지정하지 않으면 리포트 기본값 7일로 조회한다")
        void usesReportDefaultOfSevenDays() {
            givenActiveMembership();
            givenThisPeriod(7);
            givenPreviousDaily();

            service.getReport(USER_ID, null);

            // 패턴 조회 Tool의 기본값(30일)이 아니라 리포트 기본값(7일)을 넘긴다
            verify(topLearnerPatternQueryService).getTopLearnerPattern(USER_ID, 7);
        }

        @Test
        @DisplayName("지정한 기간은 그대로 상위권 비교에 넘긴다")
        void passesGivenPeriodThrough() {
            givenActiveMembership();
            givenThisPeriod(30);
            givenPreviousDaily();

            service.getReport(USER_ID, 30);

            verify(topLearnerPatternQueryService).getTopLearnerPattern(USER_ID, 30);
        }

        @Test
        @DisplayName("결과의 기간은 상위권 비교가 확정한 값을 따른다")
        void reusesPeriodResolvedByThisPeriodQuery() {
            givenActiveMembership();
            // 서버가 7일로 확정한 상황
            givenThisPeriod(7);
            givenPreviousDaily();

            LearningReportResult result = service.getReport(USER_ID, null);

            assertThat(result.periodDays()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("직전 기간 경계")
    class PreviousPeriodBoundary {

        @Test
        @DisplayName("7일 리포트의 직전 구간은 이번 구간 바로 앞의 7일이다")
        void previousWindowSitsRightBeforeCurrentWindow() {
            givenActiveMembership();
            givenThisPeriod(7);
            givenPreviousDaily();

            service.getReport(USER_ID, 7);

            // 이번 기간이 3/4~3/10이므로 직전은 2/25~3/3 (겹치지 않고 맞닿는다)
            verify(studyRecordQueryRepository).findDailyStudySeconds(
                    MEMBERSHIP_ID,
                    LocalDate.parse("2026-02-25"),
                    LocalDate.parse("2026-03-03"));
        }

        @Test
        @DisplayName("30일 리포트도 같은 규칙으로 직전 30일을 잡는다")
        void appliesSameRuleToLongerPeriods() {
            givenActiveMembership();
            givenThisPeriod(30);
            givenPreviousDaily();

            service.getReport(USER_ID, 30);

            // 이번 기간이 2/9~3/10이므로 직전은 1/10~2/8
            verify(studyRecordQueryRepository).findDailyStudySeconds(
                    MEMBERSHIP_ID,
                    LocalDate.parse("2026-01-10"),
                    LocalDate.parse("2026-02-08"));
        }

        @Test
        @DisplayName("1일 리포트의 직전 구간은 어제 하루다")
        void previousWindowOfSingleDayReportIsYesterday() {
            givenActiveMembership();
            givenThisPeriod(1);
            givenPreviousDaily();

            service.getReport(USER_ID, 1);

            verify(studyRecordQueryRepository).findDailyStudySeconds(
                    MEMBERSHIP_ID,
                    LocalDate.parse("2026-03-09"),
                    LocalDate.parse("2026-03-09"));
        }
    }

    @Nested
    @DisplayName("직전 기간 집계")
    class PreviousPeriodAggregation {

        @Test
        @DisplayName("직전 기간의 공부 시간을 분으로 합산한다")
        void sumsPreviousStudyMinutes() {
            givenActiveMembership();
            givenThisPeriod(7);
            givenPreviousDaily(
                    daily("2026-02-25", 1800),
                    daily("2026-02-26", 3600),
                    daily("2026-03-01", 900)
            );

            LearningReportResult result = service.getReport(USER_ID, 7);

            assertThat(result.previousTotalStudyMinutes()).isEqualTo(105);
        }

        @Test
        @DisplayName("공부 시간이 0인 날은 학습일로 세지 않는다")
        void excludesZeroSecondDaysFromStudyDayCount() {
            givenActiveMembership();
            givenThisPeriod(7);
            givenPreviousDaily(
                    daily("2026-02-25", 1800),
                    daily("2026-02-26", 0),
                    daily("2026-02-27", 600)
            );

            LearningReportResult result = service.getReport(USER_ID, 7);

            assertThat(result.previousStudyDayCount()).isEqualTo(2);
            assertThat(result.previousTotalStudyMinutes()).isEqualTo(40);
        }

        @Test
        @DisplayName("직전 기간에 기록이 없으면 0으로 채운다")
        void fillsZeroWhenPreviousPeriodHasNoRecord() {
            givenActiveMembership();
            givenThisPeriod(7);
            givenPreviousDaily();

            LearningReportResult result = service.getReport(USER_ID, 7);

            assertThat(result.previousTotalStudyMinutes()).isZero();
            assertThat(result.previousStudyDayCount()).isZero();
        }
    }

    @Nested
    @DisplayName("이번 기간 재사용")
    class ThisPeriodDelegation {

        @Test
        @DisplayName("상위권 비교 결과를 그대로 담아 상태값까지 전달한다")
        void carriesThisPeriodResultAsIs() {
            givenActiveMembership();
            TopLearnerPatternResult thisPeriod =
                    TopLearnerPatternResult.insufficientSample(7, 5);
            given(topLearnerPatternQueryService.getTopLearnerPattern(USER_ID, 7))
                    .willReturn(thisPeriod);
            givenPreviousDaily();

            LearningReportResult result = service.getReport(USER_ID, 7);

            // 익명성 하한에 걸린 상태를 리포트가 감추지 않는다
            assertThat(result.thisPeriod()).isSameAs(thisPeriod);
            assertThat(result.thisPeriod().status())
                    .isEqualTo(TopLearnerPatternResult.Status.INSUFFICIENT_SAMPLE);
        }
    }

    // --- 픽스처 ---

    private void givenActiveMembership() {
        CohortMembership membership = mock(CohortMembership.class);
        given(membership.getId()).willReturn(MEMBERSHIP_ID);
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(membership);
    }

    private void givenThisPeriod(int periodDays) {
        given(topLearnerPatternQueryService.getTopLearnerPattern(eq(USER_ID), any()))
                .willReturn(TopLearnerPatternResult.noData(periodDays, 12));
    }

    private void givenPreviousDaily(DailyStudySecondsResult... daily) {
        given(studyRecordQueryRepository.findDailyStudySeconds(eq(MEMBERSHIP_ID), any(), any()))
                .willReturn(List.of(daily));
    }

    private DailyStudySecondsResult daily(String date, long studySeconds) {
        return new DailyStudySecondsResult(LocalDate.parse(date), studySeconds);
    }
}
