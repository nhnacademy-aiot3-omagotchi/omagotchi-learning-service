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
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class KmaWeatherApiClient implements WeatherApiClient {

    private static final String NORMAL_SERVICE_CODE = "00";
    private static final int NUM_OF_ROWS = 1000; // totalCount가 수백 건이라 넉넉히 잡음
    private static final DateTimeFormatter BASE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
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

        return KmaForecastPivoter.pivot(response);
    }

    private void validate(KmaForecastResponse response) {
        if (Objects.isNull(response) || Objects.isNull(response.response())) {
            log.warn("[KmaWeatherApiClient] KMA 응답이 비어있음");
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE, "KMA 응답이 비어있습니다.");
        }

        KmaForecastResponse.Header header = response.response().header();
        if (Objects.isNull(header)) {
            log.warn("[KmaWeatherApiClient] KMA 응답에 header가 없음");
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE, "KMA 응답에 header가 없습니다.");
        }

        // resultCode 검사는 반드시 body 검사보다 먼저 해야 함
        // KMA는 오류 응답(예: resultCode 10)에서 body를 통쨰로 내려주지 않기 때문에, 순서가 바뀌면 정상적인 오류 상황에서 NPE가 남
        if (!NORMAL_SERVICE_CODE.equals(header.resultCode())) {
            log.warn("[KmaWeatherApiClient] KMA 비정상 응답 - resultCode = {}, resultMsg = {}", header.resultCode(), header.resultMsg());
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                    "KMA resultCode = %s (%s)".formatted(header.resultCode(), header.resultMsg())
            );
        }

        // 정상 응답인데 예보 항목이 없으면 응답이 깨진 것으로 봄
        // 데이터가 없거나 요청이 잘못된 경우엔 KMA가 resultCode(03, 10 등)로 알려주기 때문
        if (this.hasNoItems(response)) {
            log.warn("[KmaWeatherApiClient] KMA 정상 응답인데 예보 항목이 없음");
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                    "KMA 정상 응답에 예보 항목이 없습니다."
            );
        }
    }

    private boolean hasNoItems(KmaForecastResponse response) {
        KmaForecastResponse.Body body = response.response().body();

        return Objects.isNull(body)
                || Objects.isNull(body.items())
                || Objects.isNull(body.items().item())
                || body.items().item().isEmpty();
    }
}
