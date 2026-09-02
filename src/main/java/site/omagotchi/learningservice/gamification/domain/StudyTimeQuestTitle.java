package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 목표 시간을 사용자에게 보여줄 제목으로 만든다.
 *
 * <p>제목은 발급 시점에 행으로 복사되므로 사용자마다 다른 문구를 가질 수 있다.
 * 진행도를 노출하지 않는 MVP에서는 이 제목이 목표를 전달하는 유일한 통로다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StudyTimeQuestTitle {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int MINUTES_PER_HOUR = 60;

    public static String of(int targetSeconds) {
        // 초 단위는 화면에서 의미가 없으므로 분으로 반올림한다.
        long totalMinutes = Math.round((double) targetSeconds / SECONDS_PER_MINUTE);
        long hours = totalMinutes / MINUTES_PER_HOUR;
        long minutes = totalMinutes % MINUTES_PER_HOUR;

        if (hours == 0) {
            return "오늘 %d분 공부하기".formatted(minutes);
        }
        if (minutes == 0) {
            return "오늘 %d시간 공부하기".formatted(hours);
        }
        return "오늘 %d시간 %d분 공부하기".formatted(hours, minutes);
    }
}
