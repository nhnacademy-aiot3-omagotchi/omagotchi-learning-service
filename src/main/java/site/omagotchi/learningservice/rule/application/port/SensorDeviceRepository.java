package site.omagotchi.learningservice.rule.application.port;


public interface SensorDeviceRepository {

    boolean existsByDeviceEui(String deviceEui);
}
