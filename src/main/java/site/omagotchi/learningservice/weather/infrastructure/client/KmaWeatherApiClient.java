package site.omagotchi.learningservice.weather.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.weather.infrastructure.BaseTime;
import site.omagotchi.learningservice.weather.infrastructure.KmaBaseTimeCalculator;
import site.omagotchi.learningservice.weather.infrastructure.KmaForecastResponse;
import site.omagotchi.learningservice.weather.application.port.WeatherApiClient;
import site.omagotchi.learningservice.weather.domain.PrecipitationType;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class KmaWeatherApiClient implements WeatherApiClient {

    private static final String NORMAL_SERVICE_CODE = "00";
    private static final int NUM_OF_ROWS = 1000; // totalCount가 수백 건이라 넉넉히 잡음
    private static final DateTimeFormatter BASE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FCST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient kmaRestClient;
    private final KmaProperties kmaProperties;
    private final Clock clock;

    /**
     * 1. 지금이 몇 시인지 계산 -> base_date/base_time 결정
     * 2. KMA한테 HTTP 요청 -> KmaForecastResponse(플랫한 로우 덩어리) 받음
     * 3. 응답 검증 (resultCode 확인)
     * 4. 플랫한 로우들을 시간대별로 조립(pivot) -> WeatherForecast 리스트
     */
    @Override
    public List<WeatherForecast> getForecast(int nx, int ny) {
        BaseTime baseTime = KmaBaseTimeCalculator.calculate(LocalDateTime.now(this.clock.withZone(KST)));

        log.debug("[KmaWeatherApiClient] KMA 요청 - nx = {}, ny = {}, base_date = {}, base_time = {}",
                nx, ny, baseTime.baseDate().format(BASE_DATE_FORMATTER), baseTime.baseTime());

        KmaForecastResponse response;
        try {
            response = this.kmaRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/getVilageFcst")
                            .queryParam("serviceKey", this.kmaProperties.serviceKey())
                            .queryParam("dataType", "JSON")
                            .queryParam("numOfRows", NUM_OF_ROWS)
                            .queryParam("pageNo", 1)
                            .queryParam("base_date", baseTime.baseDate().format(BASE_DATE_FORMATTER))
                            .queryParam("base_time", baseTime.baseTime())
                            .queryParam("nx", nx)
                            .queryParam("ny", ny)
                            .build())
                    .retrieve()
                    .body(KmaForecastResponse.class);
        } catch (RestClientException e) {
            log.error("[KmaWeatherApiClient] KMA 호출 실패 - nx = {}, ny = {}", nx, ny, e);
            throw new BusinessException(
                    CommonErrorCode.SERVICE_UNAVAILABLE,
                    "KMA API 호출 중 오류: " + e.getMessage(),
                    e
            );
        }

        this.validate(response);

        return this.pivot(response);
    }

    private void validate(KmaForecastResponse response) {
        if (Objects.isNull(response)) {
            log.warn("[KmaWeatherApiClient] KMA 응답이 비어있음");
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE, "KMA 응답이 비어있습니다.");
        }

        KmaForecastResponse.Header header = response.response().header();
        if (!NORMAL_SERVICE_CODE.equals(header.resultCode())) {
            log.warn("[KmaWeatherApiClient] KMA 비정상 응답 - resultCode = {}, resultMsg = {}", header.resultCode(), header.resultMsg());
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                    "KMA resultCode = %s (%s)".formatted(header.resultCode(), header.resultMsg())
            );
        }
    }

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
    private List<WeatherForecast> pivot(KmaForecastResponse response) {
        List<KmaForecastResponse.Item> items = response.response().body().items().item();

        if (items.isEmpty()) {
            log.warn("[KmaWeatherApiClient] KMA 응답에 예보 항목이 없음(nx/ny 조합에 데이터 없을 수 있음)");
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
                forecasts.add(this.toWeatherForecast(entry.getKey(), entry.getValue()));
            } catch (DateTimeParseException e) {
                log.warn("[KmaWeatherApiClient] fcstDate/fcstTime 파싱 실패, 이 시간대는 건너뜀: {}", entry.getKey(), e);
            }
        }

        forecasts.sort(Comparator.comparing(WeatherForecast::forecastDate)
                .thenComparing(WeatherForecast::forecastTime));

        return forecasts;
    }

    // 묶인 한 그룹을 카테고리별로 풀어서 필드에 꽂기
    private WeatherForecast toWeatherForecast(FcstKey fcstKey, List<KmaForecastResponse.Item> items) {

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
    private Integer parseInt(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SkyCondition parseSky(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return SkyCondition.fromCode(value);
        } catch (IllegalArgumentException e) {
            log.warn("[KmaWeatherApiClient] 알 수 없는 SKY 코드: {}", value);
            return null;
        }
    }

    private PrecipitationType parsePty(String value) {
        if (Objects.isNull(value)) {
            return null;
        }

        try {
            return PrecipitationType.fromCode(value);
        } catch (IllegalArgumentException e) {
            log.warn("[KmaWeatherApiClient] 알 수 없는 PTY 코드: {}", value);
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
