package site.omagotchi.learningservice.weather.infrastructure.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.weather.domain.PrecipitationType;
import site.omagotchi.learningservice.weather.domain.SkyCondition;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;
import site.omagotchi.learningservice.weather.infrastructure.KmaForecastResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmaForecastPivoterTest {

    @Test
    @DisplayName("같은 시간대의 카테고리별 로우들이 하나의 예보로 합쳐진다")
    void groupsSameTimeSlotIntoOneForecast() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "TMP", "20"),
                item("20260825", "0600", "SKY", "1"),
                item("20260825", "0600", "PTY", "0"),
                item("20260825", "0600", "POP", "30"),
                item("20260825", "0600", "REH", "65"),
                item("20260825", "0600", "WSD", "2.3")
        ));

        List<WeatherForecast> result = KmaForecastPivoter.pivot(response);

        assertThat(result).hasSize(1);

        WeatherForecast forecast = result.getFirst();
        assertThat(forecast.forecastDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(forecast.forecastTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(forecast.temperatureCelsius()).isEqualTo(20);
        assertThat(forecast.skyCondition()).isEqualTo(SkyCondition.CLEAR);
        assertThat(forecast.precipitationType()).isEqualTo(PrecipitationType.NONE);
        assertThat(forecast.precipitationProbability()).isEqualTo(30);
        assertThat(forecast.humidityPercent()).isEqualTo(65);
        assertThat(forecast.windSpeedMs()).isEqualTo(2.3);
    }

    @Test
    @DisplayName("같은 날짜 안에서 시각 오름차순으로 정렬된다")
    void sortsByTimeWithinSameDate() {
        // 일부러 뒤섞어서 넣는다 (묶는 과정에서 순서가 보장되지 않으므로 정렬이 실제로 동작해야 한다)
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "1500", "TMP", "28"),
                item("20260825", "0900", "TMP", "22"),
                item("20260825", "2100", "TMP", "24"),
                item("20260825", "1200", "TMP", "26"),
                item("20260825", "0300", "TMP", "19")
        ));

        List<WeatherForecast> result = KmaForecastPivoter.pivot(response);

        assertThat(result).hasSize(5);
        assertThat(result.get(0).forecastTime()).isEqualTo(LocalTime.of(3, 0));
        assertThat(result.get(1).forecastTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(2).forecastTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(result.get(3).forecastTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.get(4).forecastTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("날짜가 바뀌어도 날짜 먼저, 그 다음 시각 순으로 정렬된다")
    void sortsByDateThenTimeAcrossDays() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260826", "0300", "TMP", "18"),
                item("20260825", "2100", "TMP", "24"),
                item("20260826", "0000", "TMP", "20"),
                item("20260825", "0900", "TMP", "22")
        ));

        List<WeatherForecast> result = KmaForecastPivoter.pivot(response);

        assertThat(result).hasSize(4);

        assertThat(result.get(0).forecastDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(result.get(0).forecastTime()).isEqualTo(LocalTime.of(9, 0));

        assertThat(result.get(1).forecastDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(result.get(1).forecastTime()).isEqualTo(LocalTime.of(21, 0));

        assertThat(result.get(2).forecastDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(result.get(2).forecastTime()).isEqualTo(LocalTime.of(0, 0));

        assertThat(result.get(3).forecastDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(result.get(3).forecastTime()).isEqualTo(LocalTime.of(3, 0));
    }

    @Test
    @DisplayName("예보 항목이 하나도 없으면 빈 리스트를 반환한다")
    void emptyItemsReturnsEmptyList() {
        KmaForecastResponse response = responseOf(List.of());

        List<WeatherForecast> result = KmaForecastPivoter.pivot(response);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("응답에 없는 카테고리는 null로 채워진다")
    void missingCategoriesBecomeNull() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "TMP", "20")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.temperatureCelsius()).isEqualTo(20);
        assertThat(forecast.skyCondition()).isNull();
        assertThat(forecast.precipitationType()).isNull();
        assertThat(forecast.precipitationProbability()).isNull();
        assertThat(forecast.humidityPercent()).isNull();
        assertThat(forecast.windSpeedMs()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 SKY 코드는 예외를 던지지 않고 null이 된다")
    void unknownSkyCodeBecomesNull() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "SKY", "9"),
                item("20260825", "0600", "TMP", "20")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.skyCondition()).isNull();
        assertThat(forecast.temperatureCelsius()).isEqualTo(20); // 나머지 필드는 살아있어야 한다
    }

    @Test
    @DisplayName("알 수 없는 PTY 코드는 예외를 던지지 않고 null이 된다")
    void unknownPtyCodeBecomesNull() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "PTY", "7"), // 초단기예보에만 있는 코드
                item("20260825", "0600", "TMP", "20")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.precipitationType()).isNull();
        assertThat(forecast.temperatureCelsius()).isEqualTo(20);
    }

    @Test
    @DisplayName("연장기간 예보의 정성정보(숫자가 아닌 값)는 null이 된다")
    void nonNumericValueBecomesNull() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260829", "0600", "WSD", "약간 강함"),
                item("20260829", "0600", "TMP", "20")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.windSpeedMs()).isNull();
        assertThat(forecast.temperatureCelsius()).isEqualTo(20);
    }

    @Test
    @DisplayName("소수점이 들어온 정수 필드는 반올림된다")
    void decimalIntegerFieldIsRounded() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "TMP", "20.6"),
                item("20260825", "0600", "REH", "64.4")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.temperatureCelsius()).isEqualTo(21);
        assertThat(forecast.humidityPercent()).isEqualTo(64);
    }

    @Test
    @DisplayName("날짜/시각 형식이 깨진 시간대는 건너뛰고 나머지는 그대로 살린다")
    void skipsUnparsableTimeSlotButKeepsOthers() {
        KmaForecastResponse response = responseOf(List.of(
                item("깨진날짜", "0600", "TMP", "20"),
                item("20260825", "0900", "TMP", "22"),
                item("20260825", "1200", "TMP", "24")
        ));

        List<WeatherForecast> result = KmaForecastPivoter.pivot(response);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).forecastTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(1).forecastTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("같은 시간대에 같은 카테고리가 중복으로 오면 첫 번째 값을 쓴다")
    void duplicateCategoryKeepsFirstValue() {
        KmaForecastResponse response = responseOf(List.of(
                item("20260825", "0600", "TMP", "20"),
                item("20260825", "0600", "TMP", "99")
        ));

        WeatherForecast forecast = KmaForecastPivoter.pivot(response).getFirst();

        assertThat(forecast.temperatureCelsius()).isEqualTo(20);
    }

    private static KmaForecastResponse responseOf(List<KmaForecastResponse.Item> items) {
        return new KmaForecastResponse(
                new KmaForecastResponse.Response(
                        new KmaForecastResponse.Header("00", "NORMAL_SERVICE"),
                        new KmaForecastResponse.Body(
                                new KmaForecastResponse.Items(items),
                                items.size(),
                                1,
                                items.size()
                        )
                )
        );
    }

    private static KmaForecastResponse.Item item(
            String fcstDate,
            String fcstTime,
            String category,
            String fcstValue
    ) {
        return new KmaForecastResponse.Item(
                "20260825", // baseDate
                "1400", // baseTime
                category,
                fcstDate,
                fcstTime,
                fcstValue,
                60, // nx
                74 // ny
        );
    }
}
