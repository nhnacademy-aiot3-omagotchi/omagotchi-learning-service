package site.omagotchi.learningservice.weather.presentation.response;

import site.omagotchi.learningservice.weather.application.result.WeatherQueryResult;
import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.util.List;

public record WeatherToolResponse(
        String status,
        List<String> candidateRegionNames, // status가 AMBIGUOUS 일때만 채워짐
        List<WeatherForecast> forecasts // status가 FOUND 일때만 채워짐
) {
    public static WeatherToolResponse from(WeatherQueryResult result) {
        return switch (result.status()) {
            case NOT_FOUND -> new WeatherToolResponse("NOT_FOUND", List.of(), List.of());
            case AMBIGUOUS -> new WeatherToolResponse(
                    "AMBIGUOUS",
                    result.candidates().stream()
                            .map(candidate -> candidate.sido() + " " + candidate.sigungu())
                            .toList(),
                    List.of()
            );
            case FOUND -> new WeatherToolResponse("FOUND", List.of(), result.forecasts());
        };
    }
}
