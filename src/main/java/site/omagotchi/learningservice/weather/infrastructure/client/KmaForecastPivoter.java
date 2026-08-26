package site.omagotchi.learningservice.weather.infrastructure.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import site.omagotchi.learningservice.weather.infrastructure.KmaForecastResponse;
import site.omagotchi.learningservice.weather.domain.PrecipitationType;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * KMA한테서 이런 식으로 응답 옴(같은 시간대인데 카테고리별로 로우가 따로따로):
 * {fcstDate: 0825, fcstTime: 0600, category: TMP, fcstValue: 20}
 * {fcstDate: 0825, fcstTime: 0600, category: SKY, fcstValue: 1}
 * {fcstDate: 0825, fcstTime: 0600, category: PTY, fcstValue: 0}
 * {fcstDate: 0825, fcstTime: 0900, category: TMP, fcstValue: 22}
 * {fcstDate: 0825, fcstTime: 0900, category: SKY, fcstValue: 3}
 * ...
 * <p>
 * 우리가 원하는 최종 결과는:
 * 06:00 → 기온 20도, 맑음
 * 09:00 → 기온 22도, 구름많음
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KmaForecastPivoter {

    private static final DateTimeFormatter BASE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FCST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    public static List<WeatherForecast> pivot(KmaForecastResponse response) {
        List<KmaForecastResponse.Item> items = extractItems(response);

        if (items.isEmpty()) {
            log.warn("[KmaForecastPivoter] KMA 응답에 예보 항목이 없음(nx/ny 조합에 데이터 없을 수 있음)");
            return List.of();
        }

        // 같은 시간대끼리 묶기
        // FcstKey(0825, 0600) 키 아래에 [TMP=20, SKY=1, PTY=0] 3개 로우를 한 묶음으로 묶고,
        // FcstKey(0825, 0900) 아래에 또 다른 3개를 묶고, ...
        Map<FcstKey, List<KmaForecastResponse.Item>> grouped = items.stream()
                .collect(Collectors.groupingBy(item -> new FcstKey(item.fcstDate(), item.fcstTime())));

        List<WeatherForecast> forecasts = new ArrayList<>();

        for (Map.Entry<FcstKey, List<KmaForecastResponse.Item>> entry : grouped.entrySet()) {
            try {
                forecasts.add(toWeatherForecast(entry.getKey(), entry.getValue()));
            } catch (DateTimeParseException e) {
                log.warn("[KmaForecastPivoter] fcstDate/fcstTime 파싱 실패, 이 시간대는 건너뜀: {}", entry.getKey(), e);
            }
        }

        forecasts.sort(Comparator.comparing(WeatherForecast::forecastDate)
                .thenComparing(WeatherForecast::forecastTime));

        return forecasts;
    }

    /**
     * item이 없으면 '예보 항목 없음'으로 봄
     * body/items 같은 상위 구조 누락은 계약 위반이라 KmaWeatherApiClient.validate()가 먼저 걸러냄
     */
    private static List<KmaForecastResponse.Item> extractItems(KmaForecastResponse response) {
        List<KmaForecastResponse.Item> items = response.response().body().items().item();

        return Objects.isNull(items)
                ? List.of()
                : items;
    }

    // 묶인 한 그룹을 카테고리별로 풀어서 필드에 꽂기
    private static WeatherForecast toWeatherForecast(FcstKey fcstKey, List<KmaForecastResponse.Item> items) {

        // {"TMP":"20", "SKY":"1", "PTY":"0"}
        Map<String, String> valueByCategory = items.stream()
                .collect(Collectors.toMap(
                        KmaForecastResponse.Item::category,
                        KmaForecastResponse.Item::fcstValue,
                        (first, second) -> first
                ));

        return new WeatherForecast(
                LocalDate.parse(fcstKey.fcstDate(), BASE_DATE_FORMATTER), // 날짜
                LocalTime.parse(fcstKey.fcstTime(), FCST_TIME_FORMATTER), // 시각
                parseInt(valueByCategory.get("TMP")), // 파싱한 값(20)
                parseSky(valueByCategory.get("SKY")), // 파싱한 값(CLEAR)
                parsePty(valueByCategory.get("PTY")), // 파싱한 값(NONE)
                parseInt(valueByCategory.get("POP")),
                parseInt(valueByCategory.get("REH")),
                parseDouble(valueByCategory.get("WSD"))
        );
    }

    /**
     * parseInt, parseDouble, parseSky, parsePty가 실패하면 예외를 던지지 않고 null로 처리함
     * 활용 가이드 문서: "연장기간(4~5일차) 예보는 PCP/SNO/WSD가 숫자 대신 코드값(정성정보)로 온다" 라고 되어있음
     * -> 4~5일 뒤 데이터를 파싱하다가 숫자 변환에 실패할 수 있음. 이걸 예외로 죽이지 않고 해당 필드만 null로 비워두는 쪽을 택했음
     */
    private static Integer parseInt(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static SkyCondition parseSky(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return SkyCondition.fromCode(value);
        } catch (IllegalArgumentException e) {
            log.warn("[KmaForecastPivoter] 알 수 없는 SKY 코드: {}", value);
            return null;
        }
    }

    private static PrecipitationType parsePty(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return PrecipitationType.fromCode(value);
        } catch (IllegalArgumentException e) {
            log.warn("[KmaForecastPivoter] 알 수 없는 PTY 코드: {}", value);
            return null;
        }
    }

    // 이 클래스 밖에서 쓸 일 없는 순수 내부 그룹핑용 key라서 별도 파일로 빼지 않음
    private record FcstKey(
            String fcstDate,
            String fcstTime
    ) {
    }
}
