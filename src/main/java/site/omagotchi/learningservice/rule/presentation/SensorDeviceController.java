package site.omagotchi.learningservice.rule.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;
import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;
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
}