package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("학습 시간 퀘스트 제목")
class StudyTimeQuestTitleTest {

    @Test
    @DisplayName("시간과 분을 함께 표기한다")
    void formatsHoursAndMinutes() {
        assertEquals("오늘 3시간 30분 공부하기", StudyTimeQuestTitle.of(12_600));
    }

    @Test
    @DisplayName("분이 0이면 시간만 표기한다")
    void omitsZeroMinutes() {
        assertEquals("오늘 4시간 공부하기", StudyTimeQuestTitle.of(14_400));
    }

    @Test
    @DisplayName("한 시간 미만이면 분만 표기한다")
    void formatsMinutesOnly() {
        assertEquals("오늘 45분 공부하기", StudyTimeQuestTitle.of(2_700));
    }

    @Test
    @DisplayName("초 단위는 분으로 반올림한다")
    void roundsSecondsToNearestMinute() {
        // 12629s = 3h30m29s -> 3h30m
        assertEquals("오늘 3시간 30분 공부하기", StudyTimeQuestTitle.of(12_629));
        // 12631s = 3h30m31s -> 3h31m
        assertEquals("오늘 3시간 31분 공부하기", StudyTimeQuestTitle.of(12_631));
    }

    @Test
    @DisplayName("상한 목표도 표기할 수 있다")
    void formatsMaximumTarget() {
        assertEquals("오늘 11시간 30분 공부하기", StudyTimeQuestTitle.of(41_400));
    }
}
