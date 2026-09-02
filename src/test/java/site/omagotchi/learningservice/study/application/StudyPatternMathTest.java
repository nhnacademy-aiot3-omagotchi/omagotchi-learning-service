package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 학습 패턴 지표의 순수 계산부
 * 하루의 원점이 자정이 아니라 KST 04:00이라, 새벽 0~4시가 "늦은 밤"으로 취급되는지가 핵심이다
 * 이 규칙이 깨지면 대표 시작 시각이 엉뚱한 값으로 바뀐다
 */
@DisplayName("학습 패턴 지표 계산")
class StudyPatternMathTest {

    @Nested
    @DisplayName("04:00 원점으로 변환")
    class ShiftedMinutes {

        @ParameterizedTest(name = "KST {0} → {1}분")
        @CsvSource({
                "2026-03-09T19:00:00Z, 0",    // KST 04:00 = 하루의 시작
                "2026-03-10T00:00:00Z, 300",  // KST 09:00 = 5시간 뒤
                "2026-03-10T14:59:00Z, 1199", // KST 23:59
                "2026-03-10T15:00:00Z, 1200", // KST 00:00 = 자정도 같은 하루의 연속
                "2026-03-10T18:59:00Z, 1439"  // KST 03:59 = 하루의 끝
        })
        @DisplayName("04:00을 0분으로 두고 하루를 1439분까지 편다")
        void convertsToMinutesFromFourAm(String instant, int expectedMinutes) {
            assertThat(StudyPatternMath.toShiftedMinutes(Instant.parse(instant)))
                    .isEqualTo(expectedMinutes);
        }

        @Test
        @DisplayName("자정 직후는 자정 직전보다 큰 값이 되어 같은 하루로 이어진다")
        void midnightDoesNotResetTheDay() {
            int beforeMidnight = StudyPatternMath.toShiftedMinutes(Instant.parse("2026-03-10T14:59:00Z"));
            int afterMidnight = StudyPatternMath.toShiftedMinutes(Instant.parse("2026-03-10T15:01:00Z"));

            assertThat(afterMidnight).isGreaterThan(beforeMidnight);
        }
    }

    @Nested
    @DisplayName("대표 시작 시각(중앙값)")
    class MedianStartTime {

        @Test
        @DisplayName("홀수 개면 가운데 값을 쓴다")
        void picksMiddleValueForOddCount() {
            // 08:00, 09:00, 13:00 → 09:00
            List<Integer> minutes = List.of(240, 300, 540);

            assertThat(StudyPatternMath.medianStartTime(minutes)).isEqualTo("09:00");
        }

        @Test
        @DisplayName("짝수 개면 가운데 두 값의 평균을 쓴다")
        void averagesTwoMiddleValuesForEvenCount() {
            // 08:00, 09:00, 10:00, 11:00 → 가운데 두 값(09:00, 10:00)의 평균 09:30
            List<Integer> minutes = List.of(240, 300, 360, 420);

            assertThat(StudyPatternMath.medianStartTime(minutes)).isEqualTo("09:30");
        }

        @Test
        @DisplayName("입력 순서가 뒤섞여 있어도 정렬해서 계산한다")
        void sortsBeforeComputing() {
            List<Integer> shuffled = List.of(540, 240, 300);

            assertThat(StudyPatternMath.medianStartTime(shuffled)).isEqualTo("09:00");
        }

        @Test
        @DisplayName("호출자가 넘긴 리스트를 정렬하느라 바꾸지 않는다")
        void doesNotMutateCallerList() {
            List<Integer> original = new java.util.ArrayList<>(List.of(540, 240, 300));

            StudyPatternMath.medianStartTime(original);

            assertThat(original).containsExactly(540, 240, 300);
        }

        @Test
        @DisplayName("이상치 하나가 대표값을 끌고 가지 않는다")
        void resistsOutliers() {
            // 평균이라면 새벽 3시 하루가 전체를 끌어내리지만, 중앙값은 09:00을 지킨다
            List<Integer> minutes = List.of(300, 300, 300, 1439);

            assertThat(StudyPatternMath.medianStartTime(minutes)).isEqualTo("09:00");
        }

        @Test
        @DisplayName("빈 목록은 계산할 수 없으므로 거부한다")
        void rejectsEmptyList() {
            assertThatThrownBy(() -> StudyPatternMath.medianStartTime(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("몰입 밀도")
    class FocusDensity {

        @ParameterizedTest(name = "공부 {0}초 / 앉은 {1}초 → {2}%")
        @CsvSource({
                "3600, 3600, 100", // 앉은 내내 공부
                "1800, 3600, 50",
                "900,  3600, 25",
                "1,    3600, 0"    // 정수 나눗셈이라 1% 미만은 0
        })
        @DisplayName("앉아 있던 시간 대비 실제 공부한 비율을 낸다")
        void calculatesRatio(long studySeconds, long occupiedSeconds, int expected) {
            assertThat(StudyPatternMath.focusDensityPercent(studySeconds, occupiedSeconds))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("공부 시간이 앉은 시간보다 길어도 100을 넘지 않는다")
        void capsAtHundred() {
            // 데이터가 어긋나도 계약(0~100)은 지킨다
            assertThat(StudyPatternMath.focusDensityPercent(7200, 3600)).isEqualTo(100);
        }

        @Test
        @DisplayName("앉은 시간이 0이면 나눗셈 대신 0을 돌려준다")
        void returnsZeroWhenNoOccupiedTime() {
            assertThat(StudyPatternMath.focusDensityPercent(3600, 0)).isZero();
        }
    }
}
