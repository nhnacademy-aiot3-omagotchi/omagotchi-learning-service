package site.omagotchi.learningservice.environment.application.port;

import java.time.Duration;

public interface ActionCoolDownStore {
    boolean tryAcquire(String key, Duration coolDown);
}
