package site.omagotchi.learningservice.environment.application.port;

import site.omagotchi.learningservice.environment.domain.SensorEvent;

import java.time.Instant;
import java.util.List;

public interface SensorEventStore {

   void save(SensorEvent event);

   List<SensorEvent> findByReceivedAt(Instant from, Instant to);
}
