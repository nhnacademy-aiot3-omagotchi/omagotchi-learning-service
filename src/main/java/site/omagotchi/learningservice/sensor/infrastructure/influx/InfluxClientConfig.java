package site.omagotchi.learningservice.sensor.infrastructure.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxClientConfig {

    /** InfluxDB 클라이언트.*/
    @Bean(destroyMethod = "close")      // 앱 종료할 때 스프링이 이 객체의 close() 메서드를 호출
    public InfluxDBClient sensorInfluxDBClient(SensorInfluxProperties properties) {
        return InfluxDBClientFactory.create(
                properties.url(),
                properties.token().toCharArray(),
                properties.org()
        );
    }
}
