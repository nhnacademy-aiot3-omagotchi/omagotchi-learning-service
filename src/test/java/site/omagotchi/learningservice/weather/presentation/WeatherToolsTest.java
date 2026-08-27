package site.omagotchi.learningservice.weather.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.weather.application.WeatherQueryService;
import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.presentation.response.WeatherToolResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LLM이 호출하는 날씨 조회 Tool")
class WeatherToolsTest {

    @Mock
    private WeatherQueryService weatherQueryService;

    @InjectMocks
    private WeatherTools weatherTools;

    @Test
    @DisplayName("region으로 WeatherQueryService를 호출하고 결과를 WeatherToolResponse로 변환해 돌려준다")
    void getWeatherDelegatesToQueryServiceAndConvertsResult() {
        given(this.weatherQueryService.query("광주 동구")).willReturn(WeatherQueryResult.notFound());

        WeatherToolResponse response = this.weatherTools.getWeather("광주 동구");

        verify(this.weatherQueryService).query("광주 동구");
        assertThat(response.status()).isEqualTo("NOT_FOUND");
    }
}