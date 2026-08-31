package site.omagotchi.learningservice.sensor.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.sensor.presentation.request.ClaimSensorDeviceRequest;
import site.omagotchi.learningservice.sensor.presentation.request.CreateSensorDeviceRequest;
import site.omagotchi.learningservice.sensor.presentation.request.UpdateSensorActiveRequest;
import site.omagotchi.learningservice.sensor.presentation.request.UpdateSensorDeviceRequest;
import site.omagotchi.learningservice.sensor.presentation.response.CreateSensorDeviceResponse;
import site.omagotchi.learningservice.sensor.presentation.response.SensorDeviceResponse;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/sensors")
@RestController
public class SensorDeviceController {
    private final SensorDeviceService sensorDeviceService;

    @GetMapping
    public List<SensorDeviceResponse> getSensors(
            @PathVariable Long cohortId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        List<SensorDeviceResult> results = sensorDeviceService.findAll(
                cohortId,
                user.userId()
        );

        return results.stream()
                .map(SensorDeviceResponse::from)
                .toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateSensorDeviceResponse create(
            @PathVariable Long cohortId,
            @Valid @RequestBody CreateSensorDeviceRequest request,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return new CreateSensorDeviceResponse(
                sensorDeviceService.create  (
                        cohortId,
                        user.userId(),
                        request.toCommand()
                ));
    }

    @PutMapping("/{deviceEui}")
    public SensorDeviceResponse update(
            @PathVariable Long cohortId,
            @PathVariable String deviceEui,
            @Valid @RequestBody UpdateSensorDeviceRequest request,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return SensorDeviceResponse.from(
                sensorDeviceService.update(
                        cohortId,
                        user.userId(),
                        deviceEui,
                        request.toCommand()
                ));
    }

    @PatchMapping("/{deviceEui}/active")
    public SensorDeviceResponse changeActive(
            @PathVariable Long cohortId,
            @PathVariable String deviceEui,
            @Valid @RequestBody UpdateSensorActiveRequest request,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return SensorDeviceResponse.from(
                sensorDeviceService.changeActive(
                        cohortId,
                        user.userId(),
                        deviceEui,
                        request.active()
                ));
    }

    /**
     * 주인 없는 센서 인계.
     *
     * <p>POST /{deviceEui} 와 충돌하지 않는다 — 경로가 두 세그먼트이고 리터럴 /claim이
     * 변수보다 먼저 매칭된다.</p>
     */
    @PostMapping("/{deviceEui}/claim")
    public SensorDeviceResponse claim(
            @PathVariable Long cohortId,
            @PathVariable String deviceEui,
            @Valid @RequestBody ClaimSensorDeviceRequest request,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return SensorDeviceResponse.from(
                sensorDeviceService.claim(
                        cohortId,
                        user.userId(),
                        deviceEui,
                        request.spaceId()
                ));
    }
}
