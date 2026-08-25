package site.omagotchi.learningservice.weather.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.weather.application.port.WeatherApiClient;
import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.domain.RegionGrid;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;
import site.omagotchi.learningservice.weather.infrastructure.CsvRegionGridResolver;

import java.util.List;

/**
 * 지역 매칭 -> (모호하면 여기서 멈춤) -> 예보 조회
 * 이 흐름을 조합하는 유스케이스 서비스
 */
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private final CsvRegionGridResolver csvRegionGridResolver;
    private final WeatherApiClient weatherApiClient;

    public WeatherQueryResult query(String region) {
        List<RegionGrid> candidates = this.csvRegionGridResolver.resolve(region);

        if (candidates.isEmpty()) {
            return WeatherQueryResult.notFound();
        }

        // 모호한 경우 예외 던지지 않고 정상적인 결과값으로 리턴함
        // 오류가 아니라 LLM이 되물어야 하는 정상 케이스라서.
        if (candidates.size() > 1) {
            return WeatherQueryResult.ambiguous(candidates);
        }

        RegionGrid regionGrid = candidates.getFirst();
        List<WeatherForecast> forecasts = this.weatherApiClient.getForecast(regionGrid.nx(), regionGrid.ny());

        return WeatherQueryResult.found(regionGrid, forecasts);
    }
}
