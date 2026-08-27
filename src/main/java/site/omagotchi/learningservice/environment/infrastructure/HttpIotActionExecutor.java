package site.omagotchi.learningservice.environment.infrastructure;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Component
public class HttpIotActionExecutor implements IotActionExecutor {
    private static final String TOKEN_HEADER = "X-iot-TOKEN";

    private final RestClient iotRestClient;
    private final EnvironmentProperties properties;

    @Override
    public IotActionResult execute(IotAction action, SensorDetection detection) {
        EnvironmentProperties.Iot iot = properties.iot();

        if(!iot.configured()){
            return IotActionResult.failure("제어기 주소 혹은 secret이 설정되지 않았습니다.");
        }

        String uri = iot.baseUrl() + "/" + action.name().toLowerCase(Locale.ROOT);

        try{
            CommandResponse response = iotRestClient.post()
                    .uri(uri)
                    .header(TOKEN_HEADER, iot.secret())
                    .body(CommandRequest.from(detection))
                    .retrieve()
                    .body(CommandResponse.class);

            if(Objects.isNull(response) || Objects.isNull(response.at)){
                return IotActionResult.failure("제어기 응답 혹은 응답 시간이 비어있습니다.");
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
