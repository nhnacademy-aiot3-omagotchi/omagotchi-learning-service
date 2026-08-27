package site.omagotchi.learningservice.weather.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 현재 시각 기준으로 조회 가능한 가장 최근 KMA 단기예보 발표시각(base_date/base_time)을 계산
 * 단기예보는 하루 8회(02, 05, 08, 11, 14, 17, 20, 23시) 발표되고, 발표 후 10분 뒤부터 API로 조회 가능함
 * 따라서, 지금 시각 기준으로 가장 최근에 이미 제공되기 시작한 발표시각을 찾아야 함
 */
public final class KmaBaseTimeCalculator {

    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int API_DELAY_MINUTES = 10;

    private KmaBaseTimeCalculator() {

    }

    /**
     * 지금 시각(now)가 주어졌을 떄, 이 시각에 KMA에 물어봤을 때 이미 나와 있는(발표되고 10분 지난) 가장 최근 발표시각을 찾는 로직
     */
    public static BaseTime calculate(LocalDateTime now) {
        LocalDate date = now.toLocalDate();

        // 인덱스를 length-1 = 7(값 23)부터 0(값 2)까지 거꾸로 훑음
        // 가장 최근 발표시각 찾는 거라서 큰 시각(늦은 시각)부터 먼저 확인해서 조건에 맞는 첫 번째 거를 바로 리턴
        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            int hour = BASE_HOURS[i];

            // 이 발표시각(hour시 00분)이 실제로 조회 가능해지는 시점 = hour시 + 10분
            LocalDateTime availableAt = LocalDateTime.of(date, LocalTime.of(hour, 0)).plusMinutes(API_DELAY_MINUTES);

            // 지금이 그 시점보다 이전이 아니면 -> 지금이 그 시점과 같거나 이후임 -> 이 발표는 이미 조회 가능하다
            if(!now.isBefore(availableAt)) {
                return new BaseTime(date, "%02d00".formatted(hour));
            }
        }

        // 오늘 02:10 이전이면, 조회 가능한 건 전날 23시 발표 뿐임
        return new BaseTime(date.minusDays(1), "2300");
    }
}
