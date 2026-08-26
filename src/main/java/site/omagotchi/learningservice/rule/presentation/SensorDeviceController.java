package site.omagotchi.learningservice.rule.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;
import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.rule.presentation.request.CreateSensorDeviceRequest;
import site.omagotchi.learningservice.rule.presentation.request.UpdateSensorActiveRequest;
import site.omagotchi.learningservice.rule.presentation.request.UpdateSensorDeviceRequest;
import site.omagotchi.learningservice.rule.presentation.response.CreateSensorDeviceResponse;
import site.omagotchi.learningservice.rule.presentation.response.SensorDeviceResponse;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api/v1/sensors")
@RestController
public class SensorDeviceController {
    private final SensorDeviceService sensorDeviceService;


    @GetMapping
    public List<SensorDeviceResponse> getSensors(){
        List<SensorDeviceResult> results = sensorDeviceService.findAll();

        List<SensorDeviceResponse> responses = new ArrayList<>();
        for(SensorDeviceResult result : results){
            responses.add(SensorDeviceResponse.from(result));
        }

        return responses;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateSensorDeviceResponse create(
            @Valid @RequestBody CreateSensorDeviceRequest request){

        return new CreateSensorDeviceResponse(
                sensorDeviceService.create(request.toCommand()));
    }

    @PutMapping("/{device-eui}")
    public SensorDeviceResponse update(
            @PathVariable("device-eui") String deviceEui,
            @Valid @RequestBody UpdateSensorDeviceRequest request){

        return SensorDeviceResponse.from(
                sensorDeviceService.update(request.toCommand(deviceEui)));
    }

    @PatchMapping("/{device-eui}/active")
    public SensorDeviceResponse changeActive(
            @PathVariable("device-eui") String deviceEui,
            @Valid @RequestBody UpdateSensorActiveRequest request){

        return SensorDeviceResponse.from(
                sensorDeviceService.changeActive(deviceEui, request.active()));
    }
}