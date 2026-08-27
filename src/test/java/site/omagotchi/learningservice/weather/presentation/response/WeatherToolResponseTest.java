package site.omagotchi.learningservice.weather.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.domain.PrecipitationType;
import site.omagotchi.learningservice.weather.domain.RegionGrid;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeatherQueryResult를 LLM 응답용 WeatherToolResponse로 변환")
class WeatherToolResponseTest {

    @Test
    @DisplayName("NOT_FOUND면 status만 채우고 나머지는 비운다")
    void fromNotFound() {
        WeatherQueryResult result = WeatherQueryResult.notFound();

        WeatherToolResponse response = WeatherToolResponse.from(result);

        assertThat(response.status()).isEqualTo("NOT_FOUND");
        assertThat(response.candidateRegionNames()).isEmpty();
        assertThat(response.forecasts()).isEmpty();
    }

    @Test
    @DisplayName("AMBIGUOUS면 candidateRegionNames를 '시도 시군구' 형태 문자열로 조합한다")
    void fromAmbiguous() {
        RegionGrid gwangjuDonggu = new RegionGrid("광주광역시", "동구", "", 60, 74);
        RegionGrid busanDonggu = new RegionGrid("부산광역시", "동구", "", 98, 75);
        WeatherQueryResult result = WeatherQueryResult.ambiguous(List.of(gwangjuDonggu, busanDonggu));

        WeatherToolResponse response = WeatherToolResponse.from(result);

        assertThat(response.status()).isEqualTo("AMBIGUOUS");
        assertThat(response.candidateRegionNames()).containsExactly("광주광역시 동구", "부산광역시 동구");
        assertThat(response.forecasts()).isEmpty();
    }

    @Test
    @DisplayName("FOUND면 forecasts를 그대로 담고 candidateRegionNames는 비운다")
    void fromFound() {
        RegionGrid gwangjuDonggu = new RegionGrid("광주광역시", "동구", "", 60, 74);
        WeatherForecast forecast = new WeatherForecast(
                LocalDate.of(2026, 8, 27),
                LocalTime.of(9, 0),
                27,
                SkyCondition.CLEAR,
                PrecipitationType.NONE,
                20,
                60,
                1.7
        );
        WeatherQueryResult result = WeatherQueryResult.found(gwangjuDonggu, List.of(forecast));

        WeatherToolResponse response = WeatherToolResponse.from(result);

        assertThat(response.status()).isEqualTo("FOUND");
        assertThat(response.candidateRegionNames()).isEmpty();
        assertThat(response.forecasts()).containsExactly(forecast);
    }
}