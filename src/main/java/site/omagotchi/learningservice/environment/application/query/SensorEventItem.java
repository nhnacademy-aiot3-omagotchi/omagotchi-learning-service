package site.omagotchi.learningservice.environment.application.query;

import site.omagotchi.learningservice.environment.domain.SensorEvent;

public record SensorEventItem (
        SensorEvent event,
        String  deviceDisplayName
){ }
