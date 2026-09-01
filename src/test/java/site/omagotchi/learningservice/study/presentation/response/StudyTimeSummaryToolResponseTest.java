package site.omagotchi.learningservice.study.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StudyTimeSummaryResult를 LLM 응답용 StudyTimeSummaryToolResponse로 변환")
class StudyTimeSummaryToolResponseTest {

    @Test
    @DisplayName("OK면 시간 요약 지표를 그대로 옮긴다")
    void fromOk() {
        StudyTimeSummaryResult result = new StudyTimeSummaryResult(
                StudyTimeSummaryResult.Status.OK, 7, 420, 5, 84);

        StudyTimeSummaryToolResponse response = StudyTimeSummaryToolResponse.from(result);

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.periodDays()).isEqualTo(7);
        assertThat(response.totalStudyMinutes()).isEqualTo(420);
        assertThat(response.studyDayCount()).isEqualTo(5);
        assertThat(response.averageStudyMinutesPerStudyDay()).isEqualTo(84);
    }

    @Test
    @DisplayName("NO_DATA면 숫자 지표를 0으로 유지한다")
    void fromNoData() {
        StudyTimeSummaryToolResponse response =
                StudyTimeSummaryToolResponse.from(StudyTimeSummaryResult.noData(7));

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.totalStudyMinutes()).isZero();
        assertThat(response.studyDayCount()).isZero();
        assertThat(response.averageStudyMinutesPerStudyDay()).isZero();
    }
}
