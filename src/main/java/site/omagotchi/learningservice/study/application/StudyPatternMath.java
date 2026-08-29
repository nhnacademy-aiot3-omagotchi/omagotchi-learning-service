package site.omagotchi.learningservice.study.application;

import site.omagotchi.learningservice.global.util.DateTimePolicy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * 학습 패턴 지표의 공용 계산. StudyPatternQueryService와 TopLearnerPatternQueryService가 공유한다.
 * 시간대와 리셋 시각은 전역 정책(DateTimePolicy)을 단일 출처로 쓴다.
 */
final class StudyPatternMath {

    // 집계일이 새벽 4시에 시작하므로, 시작 시각도 4시를 하루의 원점으로 계산한다
    static final int RESET_MINUTES = DateTimePolicy.DAILY_RESET_TIME.toSecondOfDay() / 60;

    private StudyPatternMath() {
    }

    /** 시작 시각을 "새벽 4시 원점 기준 몇 분"으로 바꾼다. 새벽 0~4시는 그날의 늦은 밤으로 취급된다. */
    static int toShiftedMinutes(Instant startTime) {
        LocalTime time = startTime.atZone(DateTimePolicy.ZONE_ID).toLocalTime();
        int minutesOfDay = time.getHour() * 60 + time.getMinute();
        return (minutesOfDay - RESET_MINUTES + 1440) % 1440;
    }

    /** 이동된 분 목록의 중앙값을 "HH:mm"으로 돌려준다. 짝수 개면 가운데 두 값의 평균. */
    static String medianStartTime(List<Integer> shiftedMinutes) {
        Collections.sort(shiftedMinutes);
        int size = shiftedMinutes.size();
        int median;
        if (size % 2 == 1) {
            median = shiftedMinutes.get(size / 2);
        } else {
            int lower = shiftedMinutes.get(size / 2 - 1);
            int upper = shiftedMinutes.get(size / 2);
            median = (lower + upper) / 2;
        }
        int minutesOfDay = (median + RESET_MINUTES) % 1440;
        return String.format("%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }
}