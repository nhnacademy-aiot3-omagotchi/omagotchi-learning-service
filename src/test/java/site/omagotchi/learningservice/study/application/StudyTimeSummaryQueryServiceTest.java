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
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;

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
 * 단순 시간 요약은 원본 세션을 읽지 않고 일별 합계 쿼리만 사용한다.
 * periodDays는 LLM이 채우므로 서버에서 기본값과 허용 범위를 확정한다.
 */
@DisplayName("학습 시간 요약 조회")
@ExtendWith(MockitoExtension.class)
class StudyTimeSummaryQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long MEMBERSHIP_ID = 42L;
    // KST 14:00이므로 집계일은 2026-03-10이다.
    private static final Instant NOW = Instant.parse("2026-03-10T05:00:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    private StudyTimeSummaryQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudyTimeSummaryQueryService(
                cohortAccessService,
                studyRecordQueryRepository,
                Clock.fixed(NOW, KST)
        );
    }

    @Nested
    @DisplayName("조회 기간 확정")
    class PeriodResolution {

        @Test
        @DisplayName("기간을 지정하지 않으면 현재 집계일을 포함한 최근 7일을 조회한다")
        void usesDefaultSevenDaysWhenNotSpecified() {
            givenActiveMembership();
            givenDailyResults();

            StudyTimeSummaryResult result = service.getSummary(USER_ID, null);

            assertThat(result.periodDays()).isEqualTo(7);
            verify(studyRecordQueryRepository).findDailyStudySeconds(
                    MEMBERSHIP_ID, LocalDate.parse("2026-03-04"), LocalDate.parse("2026-03-10"));
        }

        @ParameterizedTest(name = "{0}일")
        @ValueSource(ints = {1, 7, 90})
        @DisplayName("허용 범위(1~90일) 안의 값은 그대로 사용한다")
        void acceptsPeriodWithinAllowedRange(int periodDays) {
            givenActiveMembership();
            givenDailyResults();

            StudyTimeSummaryResult result = service.getSummary(USER_ID, periodDays);

            assertThat(result.periodDays()).isEqualTo(periodDays);
        }

        @ParameterizedTest(name = "{0}일")
        @ValueSource(ints = {0, -1, 91, 365})
        @DisplayName("허용 범위를 벗어나면 조회하지 않는다")
        void rejectsPeriodOutOfRange(int periodDays) {
            assertThatThrownBy(() -> service.getSummary(USER_ID, periodDays))
                    .isInstanceOf(BusinessException.class);

            verify(cohortAccessService, never()).requireCurrentActiveMembership(any());
            verify(studyRecordQueryRepository, never()).findDailyStudySeconds(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("요약 집계")
    class Summary {

        @Test
        @DisplayName("기록이 없으면 NO_DATA를 돌려준다")
        void returnsNoDataWhenNoStudyDayExists() {
            givenActiveMembership();
            givenDailyResults();

            StudyTimeSummaryResult result = service.getSummary(USER_ID, 7);

            assertThat(result.status()).isEqualTo(StudyTimeSummaryResult.Status.NO_DATA);
            assertThat(result.totalStudyMinutes()).isZero();
            assertThat(result.studyDayCount()).isZero();
            assertThat(result.averageStudyMinutesPerStudyDay()).isZero();
        }

        @Test
        @DisplayName("총 시간·학습일 수·공부한 날 기준 평균을 일별 집계로 계산한다")
        void aggregatesDailyStudySeconds() {
            givenActiveMembership();
            givenDailyResults(
                    daily("2026-03-08", 1_800),
                    daily("2026-03-09", 5_400),
                    daily("2026-03-10", 0)
            );

            StudyTimeSummaryResult result = service.getSummary(USER_ID, 7);

            assertThat(result.status()).isEqualTo(StudyTimeSummaryResult.Status.OK);
            assertThat(result.totalStudyMinutes()).isEqualTo(120);
            assertThat(result.studyDayCount()).isEqualTo(2);
            assertThat(result.averageStudyMinutesPerStudyDay()).isEqualTo(60);
            verify(studyRecordQueryRepository, never()).findActiveRecordsBetween(any(), any(), any());
        }

        @Test
        @DisplayName("KST 새벽 4시 전에는 전날 집계일을 종료일로 사용한다")
        void usesPreviousAggregationDateBeforeFourAmKst() {
            service = new StudyTimeSummaryQueryService(
                    cohortAccessService,
                    studyRecordQueryRepository,
                    Clock.fixed(Instant.parse("2026-03-09T17:00:00Z"), KST)
            );
            givenActiveMembership();
            givenDailyResults();

            service.getSummary(USER_ID, 1);

            verify(studyRecordQueryRepository).findDailyStudySeconds(
                    MEMBERSHIP_ID, LocalDate.parse("2026-03-09"), LocalDate.parse("2026-03-09"));
        }
    }

    private void givenActiveMembership() {
        CohortMembership membership = mock(CohortMembership.class);
        given(membership.getId()).willReturn(MEMBERSHIP_ID);
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(membership);
    }

    private void givenDailyResults(DailyStudySecondsResult... dailyResults) {
        given(studyRecordQueryRepository.findDailyStudySeconds(eq(MEMBERSHIP_ID), any(), any()))
                .willReturn(List.of(dailyResults));
    }

    private DailyStudySecondsResult daily(String aggregationDate, long studySeconds) {
        return new DailyStudySecondsResult(LocalDate.parse(aggregationDate), studySeconds);
    }
}
