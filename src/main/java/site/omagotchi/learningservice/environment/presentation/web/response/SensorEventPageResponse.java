package site.omagotchi.learningservice.environment.presentation.web.response;

import site.omagotchi.learningservice.environment.application.EnvironmentProperties;
import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.application.query.SensorEventPage;

import java.util.ArrayList;
import java.util.List;

public record SensorEventPageResponse (
        List<SensorEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int capacity,
        String retention
){
    public static SensorEventPageResponse from(SensorEventPage page, EnvironmentProperties properties){
        List<SensorEventResponse> content = new ArrayList<>();

        for(SensorEventItem item : page.items()){
            content.add(SensorEventResponse.from(item));
        }

        return new SensorEventPageResponse(
                content,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                properties.cache().capacity(),
                properties.cache().retention().toString()
        );
    }
}
