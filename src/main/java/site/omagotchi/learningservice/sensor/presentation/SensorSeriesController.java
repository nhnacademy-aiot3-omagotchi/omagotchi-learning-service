package site.omagotchi.learningservice.sensor.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.sensor.application.SensorSeriesService;
import site.omagotchi.learningservice.sensor.domain.SensorSeries;
import site.omagotchi.learningservice.sensor.presentation.response.SensorSeriesResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sensors")
public class SensorSeriesController {

    private final SensorSeriesService sensorSeriesService;

    @GetMapping("/series")
    public SensorSeriesResponse getSeries(
            @RequestParam String deviceEui,
            @RequestParam String measurement,
            @RequestParam String window) {

        SensorSeries series = sensorSeriesService.getSeries(deviceEui, measurement, window);

        return SensorSeriesResponse.from(series);
    }
}