package site.omagotchi.learningservice.study.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StudyPatternResult를 LLM 응답용 StudyPatternToolResponse로 변환")
class StudyPatternToolResponseTest {

    @Test
    @DisplayName("OK면 지표를 그대로 옮기고 status는 이름 문자열로 바꾼다")
    void fromOk() {
        StudyPatternResult result = new StudyPatternResult(
                StudyPatternResult.Status.OK, 30,
                12, 600, 20, 30, 90, "09:00", 9, 80, 3
        );

        StudyPatternToolResponse response = StudyPatternToolResponse.from(result);

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.periodDays()).isEqualTo(30);
        assertThat(response.studyDayCount()).isEqualTo(12);
        assertThat(response.totalStudyMinutes()).isEqualTo(600);
        assertThat(response.sessionCount()).isEqualTo(20);
        assertThat(response.averageSessionMinutes()).isEqualTo(30);
        assertThat(response.longestSessionMinutes()).isEqualTo(90);
        assertThat(response.typicalStartTime()).isEqualTo("09:00");
        assertThat(response.bestStartHour()).isEqualTo(9);
        assertThat(response.focusDensityPercent()).isEqualTo(80);
        assertThat(response.currentStreakDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("NO_DATA면 시각 계열은 null로 두어 모델이 없는 값을 지어내지 않게 한다")
    void fromNoData() {
        StudyPatternResult result = StudyPatternResult.noData(7);

        StudyPatternToolResponse response = StudyPatternToolResponse.from(result);

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.periodDays()).isEqualTo(7);
        // 0으로 채우면 "0시에 공부 시작"처럼 읽힐 수 있어 null이어야 한다
        assertThat(response.typicalStartTime()).isNull();
        assertThat(response.bestStartHour()).isNull();
        assertThat(response.studyDayCount()).isZero();
        assertThat(response.totalStudyMinutes()).isZero();
        assertThat(response.focusDensityPercent()).isZero();
        assertThat(response.currentStreakDays()).isZero();
    }
}
