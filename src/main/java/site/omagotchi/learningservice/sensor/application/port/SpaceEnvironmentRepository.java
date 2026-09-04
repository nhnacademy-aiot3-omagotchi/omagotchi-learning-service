package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;

import java.util.List;

public interface SpaceEnvironmentRepository {

    List<SensorReadingSnapshot> findLatestReadings(SpaceEnvironmentQuery query);
}
