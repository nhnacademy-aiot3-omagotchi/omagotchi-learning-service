package site.omagotchi.learningservice.environment.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;
import site.omagotchi.learningservice.environment.application.port.IotActionExecutor;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
public class HttpIotActionExecutor implements IotActionExecutor {
    private static final String TOKEN_HEADER = "X-iot-TOKEN";

    private final RestClient iotRestClient;
    private final EnvironmentProperties.Iot iot;

    public HttpIotActionExecutor(RestClient iotRestClient, EnvironmentProperties properties){
        this.iotRestClient = iotRestClient;
        this.iot = properties.iot();

        if(!iot.configured()) {
            log.warn("IOT_BASE_URL이 비어 있다. 룰 히트 조치가 전부 실패로 기록된다");
        }

        if(Objects.isNull(iot.secret()) || iot.secret().isBlank()){
            log.warn("IOT_ENDPOINT_SECRET비어있음. 제어기 호출이 무인증으로 나감");
        }
    }

    @Override
    public IotActionResult execute(IotAction action, SensorDetection detection) {
        if(!iot.configured()){
            return IotActionResult.failure("제어기 주소가 설정되지 않았습니다");
        }

        String uri = iot.baseUrl() + "/" + action.name().toLowerCase(Locale.ROOT);

        try{
            CommandResponse response = iotRestClient.post()
                    .uri(uri)
                    .header(TOKEN_HEADER, iot.secret())
                    .body(CommandRequest.from(detection))
                    .retrieve()
                    .body(CommandResponse.class);

            if(Objects.isNull(response)){
                return IotActionResult.failure("제어기 응답이 비었습니다.");
            }

            return new IotActionResult(response.actioned(), response.at(), response.simulated(), null);
        } catch (RestClientException e) {
            return IotActionResult.failure(e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    private record CommandRequest(
            String location,
            String measurement,
            Double value,
            String operator,
            Double threshold
    ){
        static CommandRequest from(SensorDetection sensorDetection){
            return new CommandRequest(
                    sensorDetection.location(),
                    sensorDetection.measurement(),
                    sensorDetection.value(),
                    sensorDetection.operator() == null ? null : sensorDetection.operator().name(),
                    sensorDetection.threshold()
            );
        }
    }

    private record CommandResponse(
            boolean actioned,
            Instant at,
            boolean simulated
    ){}
}
