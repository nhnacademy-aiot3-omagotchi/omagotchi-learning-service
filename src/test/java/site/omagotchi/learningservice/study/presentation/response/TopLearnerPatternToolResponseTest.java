package site.omagotchi.learningservice.study.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 변환은 단순 매핑이 아니다
 * myPattern이 없을 수 있어 중첩 변환에 null 분기가 있고, 익명성 하한에 걸린 응답이
 * 집계값을 비운 채 내려가는지가 개인정보 경계와 직결된다
 * (ADR ai-assistant/0009 기수 상위권 통계의 익명성 보호 임계값)
 */
@DisplayName("TopLearnerPatternResult를 LLM 응답용 TopLearnerPatternToolResponse로 변환")
class TopLearnerPatternToolResponseTest {

    @Test
    @DisplayName("OK면 상위권 집계와 내 패턴을 함께 담는다")
    void fromOk() {
        StudyPatternResult myPattern = new StudyPatternResult(
                StudyPatternResult.Status.OK, 30,
                8, 400, 16, 25, 60, "10:30", 10, 65, 2
        );
        TopLearnerPatternResult result = new TopLearnerPatternResult(
                TopLearnerPatternResult.Status.OK, 30,
                40, 4, 180, 20, 45, 88, "08:00", myPattern
        );

        TopLearnerPatternToolResponse response = TopLearnerPatternToolResponse.from(result);

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.periodDays()).isEqualTo(30);
        assertThat(response.cohortStudentCount()).isEqualTo(40);
        assertThat(response.topGroupSize()).isEqualTo(4);
        assertThat(response.topAverageDailyMinutes()).isEqualTo(180);
        assertThat(response.topAverageStudyDayCount()).isEqualTo(20);
        assertThat(response.topAverageSessionMinutes()).isEqualTo(45);
        assertThat(response.topFocusDensityPercent()).isEqualTo(88);
        assertThat(response.topTypicalStartTime()).isEqualTo("08:00");
        // 내 패턴은 중첩 변환되어 함께 실린다
        assertThat(response.myPattern()).isNotNull();
        assertThat(response.myPattern().status()).isEqualTo("OK");
        assertThat(response.myPattern().focusDensityPercent()).isEqualTo(65);
    }

    @Test
    @DisplayName("INSUFFICIENT_SAMPLE이면 표본 크기만 남기고 집계값과 내 패턴을 비운다")
    void fromInsufficientSample() {
        TopLearnerPatternResult result = TopLearnerPatternResult.insufficientSample(30, 5);

        TopLearnerPatternToolResponse response = TopLearnerPatternToolResponse.from(result);

        assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
        // 왜 제공할 수 없는지 모델이 설명하려면 표본 크기는 필요하다
        assertThat(response.cohortStudentCount()).isEqualTo(5);
        // 그 외에는 개인을 추정할 단서를 남기지 않는다
        assertThat(response.topGroupSize()).isZero();
        assertThat(response.topAverageDailyMinutes()).isZero();
        assertThat(response.topAverageStudyDayCount()).isZero();
        assertThat(response.topAverageSessionMinutes()).isZero();
        assertThat(response.topFocusDensityPercent()).isZero();
        assertThat(response.topTypicalStartTime()).isNull();
        assertThat(response.myPattern()).isNull();
    }

    @Test
    @DisplayName("NO_DATA도 같은 방식으로 집계값 없이 내려간다")
    void fromNoData() {
        TopLearnerPatternResult result = TopLearnerPatternResult.noData(7, 15);

        TopLearnerPatternToolResponse response = TopLearnerPatternToolResponse.from(result);

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.periodDays()).isEqualTo(7);
        assertThat(response.cohortStudentCount()).isEqualTo(15);
        assertThat(response.topGroupSize()).isZero();
        assertThat(response.topTypicalStartTime()).isNull();
        assertThat(response.myPattern()).isNull();
    }

    @Test
    @DisplayName("myPattern이 없어도 변환이 깨지지 않는다")
    void handlesNullMyPatternWithoutFailing() {
        TopLearnerPatternResult result = new TopLearnerPatternResult(
                TopLearnerPatternResult.Status.OK, 30,
                40, 4, 180, 20, 45, 88, "08:00", null
        );

        TopLearnerPatternToolResponse response = TopLearnerPatternToolResponse.from(result);

        assertThat(response.myPattern()).isNull();
        assertThat(response.topTypicalStartTime()).isEqualTo("08:00");
    }
}
