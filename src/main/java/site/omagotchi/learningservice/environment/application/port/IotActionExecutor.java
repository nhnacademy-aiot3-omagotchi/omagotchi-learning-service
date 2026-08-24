package site.omagotchi.learningservice.environment.application.port;

import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;

public interface IotActionExecutor {
    IotActionResult execute(IotAction action, SensorDetection detection);
}
