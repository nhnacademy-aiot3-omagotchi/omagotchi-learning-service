package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이 서비스의 임계값은 회귀하면 곧바로 개인정보가 노출되는 자리다
 * 표본이 작을 때 "상위권 평균"은 특정 개인의 기록과 같아지므로, 하한 미달을 걸러내는 동작을 경계값으로 고정해 둔다
 * (ADR ai-assistant/0009 기수 상위권 통계의 익명성 보호 임계값)
 */
@DisplayName("기수 상위권 학습 패턴 조회")
@ExtendWith(MockitoExtension.class)
class TopLearnerPatternQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 100L;
    private static final int PERIOD_DAYS = 30;

    // 집계일이 2026-03-10이 되도록 고정한다 (KST 04:00 이후)
    private static final Instant NOW = Instant.parse("2026-03-10T05:00:00Z");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private StudyPatternQueryService studyPatternQueryService;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    // 소속은 진입점에서 한 번만 조회해 내 패턴 조회에 넘긴다. 스텁이 같은 인스턴스를 가리켜야 한다
    @Mock
    private CohortMembership membership;

    private TopLearnerPatternQueryService service;

    @BeforeEach
    void setUp() {
        service = new TopLearnerPatternQueryService(
                studyPatternQueryService,
                cohortAccessService,
                cohortMembershipQueryService,
                studyRecordQueryRepository,
                Clock.fixed(NOW, KST)
        );
    }

    @Nested
    @DisplayName("익명성 보호 임계값")
    class AnonymityThreshold {

        @Test
        @DisplayName("기수 학생이 10명 미만이면 집계하지 않고 INSUFFICIENT_SAMPLE을 돌려준다")
        void returnsInsufficientSampleWhenCohortHasFewerThanTenStudents() {
            givenMyPatternIsOk();
            givenCohortStudents(9);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.status()).isEqualTo(TopLearnerPatternResult.Status.INSUFFICIENT_SAMPLE);
            assertThat(result.cohortStudentCount()).isEqualTo(9);
            // 학습 기록을 조회하는 단계까지 가지 않아야 한다
            verify(studyRecordQueryRepository, never())
                    .findConfirmedDurations(anyCollection(), any(), any());
        }

        @Test
        @DisplayName("기수 학생이 정확히 10명이면 집계를 진행한다")
        void proceedsWhenCohortHasExactlyTenStudents() {
            givenMyPatternIsOk();
            givenCohortStudents(10);
            givenConfirmedDurations(10);
            givenTopRecords(3);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.status()).isEqualTo(TopLearnerPatternResult.Status.OK);
        }

        @Test
        @DisplayName("기간 내 기록자가 3명 미만이면 NO_DATA를 돌려준다")
        void returnsNoDataWhenFewerThanThreeMembersHaveRecords() {
            givenMyPatternIsOk();
            givenCohortStudents(20);
            givenConfirmedDurations(2);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.status()).isEqualTo(TopLearnerPatternResult.Status.NO_DATA);
            assertThat(result.cohortStudentCount()).isEqualTo(20);
            // 상위 그룹 기록을 꺼내는 단계까지 가지 않아야 한다
            verify(studyRecordQueryRepository, never())
                    .findActiveRecordsBetweenForMemberships(anyCollection(), any(), any());
        }

        @Test
        @DisplayName("학생이 20명이라 10%가 2명이어도 상위 그룹은 최소 3명으로 올린다")
        void raisesTopGroupToMinimumThreeWhenTenPercentIsSmaller() {
            givenMyPatternIsOk();
            givenCohortStudents(20);
            givenConfirmedDurations(20);
            givenTopRecords(3);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.topGroupSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("학생이 50명이면 상위 그룹은 10%인 5명이 된다")
        void usesTenPercentWhenItExceedsMinimum() {
            givenMyPatternIsOk();
            givenCohortStudents(50);
            givenConfirmedDurations(50);
            givenTopRecords(5);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.topGroupSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("상위 그룹 선정")
    class TopGroupSelection {

        @Test
        @DisplayName("총 공부 시간이 많은 순으로 상위 그룹을 고른다")
        void selectsTopGroupByTotalStudySeconds() {
            givenMyPatternIsOk();
            givenCohortStudents(10);

            // membershipId 1~10, 공부 시간은 역순(1번이 가장 적고 10번이 가장 많다)
            List<MemberStudyDurationResult> durations = new ArrayList<>();
            for (long id = 1; id <= 10; id++) {
                durations.add(new MemberStudyDurationResult(id, id * 1000));
            }
            given(studyRecordQueryRepository.findConfirmedDurations(anyCollection(), any(), any()))
                    .willReturn(durations);
            given(studyRecordQueryRepository.findActiveRecordsBetweenForMemberships(anyCollection(), any(), any()))
                    .willReturn(List.of(recordOf(10L, "2026-03-09T01:00:00Z", 3600)));

            service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            // 상위 3명은 공부 시간이 가장 많은 10, 9, 8번이어야 한다
            verify(studyRecordQueryRepository)
                    .findActiveRecordsBetweenForMemberships(
                            org.mockito.ArgumentMatchers.argThat(
                                    ids -> ids.containsAll(List.of(10L, 9L, 8L)) && ids.size() == 3),
                            any(), any());
        }

        @Test
        @DisplayName("상위 그룹의 기록이 하나도 없으면 NO_DATA를 돌려준다")
        void returnsNoDataWhenTopGroupHasNoRecords() {
            givenMyPatternIsOk();
            givenCohortStudents(10);
            givenConfirmedDurations(10);
            given(studyRecordQueryRepository.findActiveRecordsBetweenForMemberships(anyCollection(), any(), any()))
                    .willReturn(List.of());

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            assertThat(result.status()).isEqualTo(TopLearnerPatternResult.Status.NO_DATA);
        }
    }

    @Nested
    @DisplayName("응답에 담기는 값")
    class ResponseContent {

        @Test
        @DisplayName("하한에 걸려 거부해도 개인을 특정할 값은 담지 않는다")
        void insufficientSampleCarriesNoIndividualData() {
            givenMyPatternIsOk();
            givenCohortStudents(5);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, PERIOD_DAYS);

            // 표본 크기 외에는 어떤 집계값도 채우지 않는다
            assertThat(result.topGroupSize()).isZero();
            assertThat(result.topAverageDailyMinutes()).isZero();
            assertThat(result.topAverageSessionMinutes()).isZero();
            assertThat(result.topFocusDensityPercent()).isZero();
            assertThat(result.topTypicalStartTime()).isNull();
            assertThat(result.myPattern()).isNull();
        }

        @Test
        @DisplayName("기간은 내 패턴 조회가 확정한 값을 그대로 쓴다")
        void reusesPeriodDaysResolvedByMyPatternQuery() {
            // 서버가 기본값 30일로 확정한 상황 (호출자는 null을 넘겼다)
            given(studyPatternQueryService.getPattern(membership, null))
                    .willReturn(okPattern(30));
            givenCohortStudents(5);

            TopLearnerPatternResult result = service.getTopLearnerPattern(USER_ID, null);

            assertThat(result.periodDays()).isEqualTo(30);
        }
    }

    private void givenMyPatternIsOk() {
        given(studyPatternQueryService.getPattern(membership, PERIOD_DAYS))
                .willReturn(okPattern(PERIOD_DAYS));
    }

    private StudyPatternResult okPattern(int periodDays) {
        return new StudyPatternResult(
                StudyPatternResult.Status.OK, periodDays,
                10, 600, 20, 30, 90, "09:00", 9, 80, 3
        );
    }

    private void givenCohortStudents(int count) {
        given(membership.getCohortId()).willReturn(COHORT_ID);
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(membership);

        List<CohortMembershipView> students = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            students.add(new CohortMembershipView(id, COHORT_ID, UUID.randomUUID()));
        }
        given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID)).willReturn(students);
    }

    private void givenConfirmedDurations(int count) {
        List<MemberStudyDurationResult> durations = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            durations.add(new MemberStudyDurationResult(id, 1000L * id));
        }
        given(studyRecordQueryRepository.findConfirmedDurations(anyCollection(), any(), any()))
                .willReturn(durations);
    }

    private void givenTopRecords(int memberCount) {
        List<StudyRecord> records = new ArrayList<>();
        for (long id = 1; id <= memberCount; id++) {
            records.add(recordOf(id, "2026-03-09T01:00:00Z", 3600));
        }
        given(studyRecordQueryRepository.findActiveRecordsBetweenForMemberships(anyCollection(), any(), any()))
                .willReturn(records);
    }

    private StudyRecord recordOf(Long membershipId, String startTime, long studySeconds) {
        Instant start = Instant.parse(startTime);
        return StudyRecord.create(membershipId, start, start.plusSeconds(studySeconds), studySeconds);
    }
}
