package site.omagotchi.learningservice.environment.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/simulator/iot")
@ConditionalOnProperty(prefix = "environment.iot.simulator", name = "enabled", havingValue = "true")
public class SimulateController {
    private static final String TOKEN_HEADER = "X-iot-TOKEN";

    private final Clock clock;
    private final String exceptedSecret;


    public SimulateController(Clock clock, EnvironmentProperties properties){
        this.clock = clock;
        this.exceptedSecret = properties.iot().secret();

        log.warn("시뮬레이터 컨트롤러가 등록되어있음. 시뮬레이터가 필요없으면 environment.iot.simulator.enabled=false로 설정해주세요.");
    }


    @PostMapping("/{action}")
    public ResponseEntity<Map<String, Object>> execute(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestBody Map<String, String> command,
            @RequestParam(defaultValue = "true") boolean success){


        if(!authorized(token)){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(
                Map.of(
                "actioned", success,
                "at", clock.instant(),
                "simulated", true)
        );
    }


    private boolean authorized(String token){
        if(Objects.isNull(exceptedSecret) || exceptedSecret.isBlank()){
            return true;
        }

        if(Objects.isNull(token)){
            return false;
        }

        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                exceptedSecret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
