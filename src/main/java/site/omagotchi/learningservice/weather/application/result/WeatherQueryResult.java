package site.omagotchi.learningservice.weather.application.result;

import site.omagotchi.learningservice.weather.domain.RegionGrid;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.util.List;

public record WeatherQueryResult(
        Status status,
        List<RegionGrid> candidates, // status가 AMBIGUOUS 일때만 채워짐
        RegionGrid resolvedRegion, // status가 FOUND 일때만 채워짐
        List<WeatherForecast> forecasts // status가 FOUND 일떄만 채워짐
) {
    // 지역 검색 결과
    public enum Status {
        FOUND, // 찾음
        AMBIGUOUS, // 모호함
        NOT_FOUND // 못 찾음
    }

    public static WeatherQueryResult notFound() {
        return new WeatherQueryResult(Status.NOT_FOUND, List.of(), null, List.of());
    }

    public static WeatherQueryResult ambiguous(List<RegionGrid> candidates) {
        return new WeatherQueryResult(Status.AMBIGUOUS, candidates, null, List.of());
    }

    public static WeatherQueryResult found(RegionGrid region, List<WeatherForecast> forecasts) {
        return new WeatherQueryResult(Status.FOUND, List.of(), region, forecasts);
    }
}
