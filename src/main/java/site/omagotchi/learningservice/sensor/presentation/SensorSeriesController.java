package site.omagotchi.learningservice.sensor.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.sensor.application.SensorSeriesService;
import site.omagotchi.learningservice.sensor.application.SpaceSeriesService;
import site.omagotchi.learningservice.sensor.application.result.SensorSeries;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.presentation.response.SensorSeriesResponse;
import site.omagotchi.learningservice.sensor.presentation.response.SpaceSeriesResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sensors")
public class SensorSeriesController {

    private final SensorSeriesService sensorSeriesService;
    private final SpaceSeriesService spaceSeriesService;

    @GetMapping("/series")
    public SensorSeriesResponse getSeries(
            @RequestParam @NotBlank
            @Pattern(regexp = "^[0-9a-fA-F]{16}$", message = "deviceEui 형식이 올바르지 않습니다.") String deviceEui,
            @RequestParam @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "measurement 형식이 올바르지 않습니다.") String measurement,
            @RequestParam @NotBlank String window) {

        SensorSeries series = sensorSeriesService.getSeries(deviceEui, measurement, window);

        return SensorSeriesResponse.from(series);
    }

    @GetMapping("/space-series")
    public SpaceSeriesResponse getSpaceSeries(
            @RequestParam @NotBlank
            @Pattern(regexp = "^[가-힣A-Za-z0-9 _-]{1,32}$", message = "location 형식이 올바르지 않습니다.") String location,
            @RequestParam @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "measurement 형식이 올바르지 않습니다.") String measurement,
            @RequestParam @NotBlank String window) {

        SpaceSeries series = spaceSeriesService.getSpaceSeries(location, measurement, window);

        return SpaceSeriesResponse.from(series);
    }
}