package site.omagotchi.learningservice.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 특정 날짜·시각 한 시점에 대한 예보 정보
 * KMA 응답(KmaForecastResponse.Item)은 카테고리(TMP, SKY, PTY...)별로 로우가 따로따로 오기 때문에,
 * 같은 fcstDate + fcstTime을 가진 로우들을 모아서 이 하나의 객체로 조립한 결과가 이 레코드
 */
public record WeatherForecast(
        LocalDate forecastDate,
        LocalTime forecastTime,
        Integer temperatureCelsius, // TMP: 1시간 기온(.c)
        SkyCondition skyCondition, // SKY: 하늘상태
        PrecipitationType precipitationType, // PTY: 강수형태
        Integer precipitationProbability, // POP: 강수확률(%)
        Integer humidityPercent, // REH: 습도(%)
        Double windSpeedMs // WSD: 풍속(m/s)
) {
}
