package site.omagotchi.learningservice.environment.presentation.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;
import site.omagotchi.learningservice.environment.application.SensorEventQueryService;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.environment.presentation.web.response.SensorEventPageResponse;

import java.time.Instant;

@RequestMapping("/api/v1/sensors/events")
@RequiredArgsConstructor
@RestController
public class SensorEventController {

    private final SensorEventQueryService queryService;
    private final EnvironmentProperties properties;

    @GetMapping
    public SensorEventPageResponse getEvents(
            @RequestParam(required = false) SensorEventType type,
            @RequestParam(required = false) String deviceEui,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size){

        return SensorEventPageResponse.from(
                queryService.getEvents(type, deviceEui, from, to, page, size),
                properties
        );
    }
}
