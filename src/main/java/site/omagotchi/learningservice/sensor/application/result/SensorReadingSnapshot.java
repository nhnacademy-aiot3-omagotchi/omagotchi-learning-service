package site.omagotchi.learningservice.sensor.application.result;

import java.time.Instant;

public record SensorReadingSnapshot (
        String deviceEui,
        String measurement,
        double value,
        Instant time
){ }