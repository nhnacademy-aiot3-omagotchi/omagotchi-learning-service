package site.omagotchi.learningservice.weather.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.weather.application.port.WeatherApiClient;
import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.domain.PrecipitationType;
import site.omagotchi.learningservice.weather.domain.RegionGrid;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;
import site.omagotchi.learningservice.weather.infrastructure.CsvRegionGridResolver;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("지역 매칭과 예보 조회 조합")
class WeatherQueryServiceTest {

    private static final RegionGrid GWANGJU_DONGGU = new RegionGrid("전남광주통합특별시", "동구", "", 60, 74);
    private static final RegionGrid BUSAN_DONGGU = new RegionGrid("부산광역시", "동구", "", 98, 75);

    @Mock
    private CsvRegionGridResolver csvRegionGridResolver;

    @Mock
    private WeatherApiClient weatherApiClient;

    @InjectMocks
    private WeatherQueryService weatherQueryService;

    @Test
    @DisplayName("지역을 못 찾으면 NOT_FOUND를 반환하고 KMA를 호출하지 않는다")
    void returnsNotFoundWithoutCallingApi() {
        given(csvRegionGridResolver.resolve("없는지역")).willReturn(List.of());

        WeatherQueryResult result = this.weatherQueryService.query("없는지역");

        assertThat(result.status()).isEqualTo(WeatherQueryResult.Status.NOT_FOUND);
        assertThat(result.resolvedRegion()).isNull();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.forecasts()).isEmpty();

        verify(weatherApiClient, never()).getForecast(anyInt(), anyInt());
    }

    @Test
    @DisplayName("후보가 여러 곳이면 AMBIGUOUS를 반환하고 KMA를 호출하지 않는다")
    void returnsAmbiguousWithoutCallingApi() {
        given(csvRegionGridResolver.resolve("동구"))
                .willReturn(List.of(GWANGJU_DONGGU, BUSAN_DONGGU));

        WeatherQueryResult result = this.weatherQueryService.query("동구");

        assertThat(result.status()).isEqualTo(WeatherQueryResult.Status.AMBIGUOUS);
        assertThat(result.candidates()).containsExactly(GWANGJU_DONGGU, BUSAN_DONGGU);
        assertThat(result.resolvedRegion()).isNull();
        assertThat(result.forecasts()).isEmpty();

        // 어느 지역인지 확정되지 않았으므로 외부 호출을 하면 안 된다
        verify(weatherApiClient, never()).getForecast(anyInt(), anyInt());
    }

    @Test
    @DisplayName("지역이 하나로 좁혀지면 그 격자로 예보를 조회해 FOUND를 반환한다")
    void returnsFoundWithForecasts() {
        WeatherForecast forecast = new WeatherForecast(
                LocalDate.of(2026, 8, 25),
                LocalTime.of(15, 0),
                33,
                SkyCondition.CLOUDY,
                PrecipitationType.NONE,
                30,
                65,
                1.7
        );

        given(csvRegionGridResolver.resolve("광주 동구")).willReturn(List.of(GWANGJU_DONGGU));
        given(weatherApiClient.getForecast(60, 74)).willReturn(List.of(forecast));

        WeatherQueryResult result = this.weatherQueryService.query("광주 동구");

        assertThat(result.status()).isEqualTo(WeatherQueryResult.Status.FOUND);
        assertThat(result.resolvedRegion()).isEqualTo(GWANGJU_DONGGU);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.forecasts()).containsExactly(forecast);

        // 매칭된 지역의 격자좌표가 그대로 전달되어야 한다
        verify(weatherApiClient).getForecast(60, 74);
    }
}
