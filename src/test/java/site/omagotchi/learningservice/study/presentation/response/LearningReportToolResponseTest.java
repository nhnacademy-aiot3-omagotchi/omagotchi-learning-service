package site.omagotchi.learningservice.study.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리포트 응답은 자체 상태값이 없고 thisPeriod에 상위권 비교 응답을 통째로 싣는다
 * 그 안의 상태값이 그대로 보존되어야 모델이 "비교는 못 한다"를 판단할 수 있다
 */
@DisplayName("LearningReportResult를 LLM 응답용 LearningReportToolResponse로 변환")
class LearningReportToolResponseTest {

    @Test
    @DisplayName("이번 기간과 직전 기간 값을 함께 담는다")
    void carriesBothPeriods() {
        StudyPatternResult myPattern = new StudyPatternResult(
                StudyPatternResult.Status.OK, 7,
                5, 300, 10, 30, 60, "09:00", 9, 75, 2
        );
        TopLearnerPatternResult thisPeriod = new TopLearnerPatternResult(
                TopLearnerPatternResult.Status.OK, 7,
                30, 3, 150, 6, 40, 85, "08:30", myPattern
        );
        LearningReportResult result = new LearningReportResult(
                7, 240, 4, thisPeriod, StudyEnvironmentResult.noData(7));

        LearningReportToolResponse response = LearningReportToolResponse.from(result);

        assertThat(response.periodDays()).isEqualTo(7);
        assertThat(response.previousTotalStudyMinutes()).isEqualTo(240);
        assertThat(response.previousStudyDayCount()).isEqualTo(4);
        assertThat(response.thisPeriod()).isNotNull();
        assertThat(response.thisPeriod().status()).isEqualTo("OK");
        assertThat(response.thisPeriod().myPattern().totalStudyMinutes()).isEqualTo(300);
    }

    @Test
    @DisplayName("이번 기간이 익명성 하한에 걸려도 그 상태를 감추지 않는다")
    void preservesInsufficientSampleStatus() {
        LearningReportResult result = new LearningReportResult(
                7, 100, 2, TopLearnerPatternResult.insufficientSample(7, 5),
                StudyEnvironmentResult.noData(7));

        LearningReportToolResponse response = LearningReportToolResponse.from(result);

        // 상위권 비교는 못 하지만 직전 기간 비교는 여전히 가능하다
        assertThat(response.thisPeriod().status()).isEqualTo("INSUFFICIENT_SAMPLE");
        assertThat(response.thisPeriod().myPattern()).isNull();
        assertThat(response.previousTotalStudyMinutes()).isEqualTo(100);
        assertThat(response.previousStudyDayCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("직전 기간에 기록이 없으면 0으로 내려간다")
    void carriesZeroWhenPreviousPeriodIsEmpty() {
        LearningReportResult result = new LearningReportResult(
                7, 0, 0, TopLearnerPatternResult.noData(7, 12),
                StudyEnvironmentResult.noData(7));

        LearningReportToolResponse response = LearningReportToolResponse.from(result);

        assertThat(response.previousTotalStudyMinutes()).isZero();
        assertThat(response.previousStudyDayCount()).isZero();
        assertThat(response.thisPeriod().status()).isEqualTo("NO_DATA");
    }
}
