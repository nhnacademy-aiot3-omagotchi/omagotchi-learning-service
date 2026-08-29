package site.omagotchi.learningservice.study.application;

import site.omagotchi.learningservice.global.util.DateTimePolicy;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
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
        if (shiftedMinutes.isEmpty()) {
            throw new IllegalArgumentException("shiftedMinutes는 비어 있을 수 없습니다");
        }
        // 호출자가 넘긴 리스트를 바꾸지 않도록 사본을 정렬한다
        List<Integer> sorted = new ArrayList<>(shiftedMinutes);
        Collections.sort(sorted);
        int size = sorted.size();
        int median;
        if (size % 2 == 1) {
            median = sorted.get(size / 2);
        } else {
            int lower = sorted.get(size / 2 - 1);
            int upper = sorted.get(size / 2);
            median = (lower + upper) / 2;
        }
        int minutesOfDay = (median + RESET_MINUTES) % 1440;
        return String.format("%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /** 앉아 있던 시간 중 실제 공부한 비율(0~100). 분모가 0이면 0을 돌려준다. */
    static int focusDensityPercent(long totalStudySeconds, long totalOccupiedSeconds) {
        if (totalOccupiedSeconds <= 0) {
            return 0;
        }
        // 100을 먼저 곱해야 정수 나눗셈에서 0이 되지 않는다. 상한 100으로 계약을 지킨다
        return (int) Math.min(100L, totalStudySeconds * 100 / totalOccupiedSeconds);
    }
}