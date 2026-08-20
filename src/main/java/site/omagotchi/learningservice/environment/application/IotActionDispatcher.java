package site.omagotchi.learningservice.environment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.environment.application.port.ActionCoolDownStore;
import site.omagotchi.learningservice.environment.application.port.IotActionExecutor;
import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.*;

import java.util.Objects;
import java.util.Optional;

/** 룰 히트 조치 실행, 결과 반환(ActionOutcome)*/
@Slf4j
@RequiredArgsConstructor
@Service
public class IotActionDispatcher {
    private static final String COOLDOWN_KEY_PREFIX = "omagotchi:iot:cooldown:";

    private final ActionCoolDownStore coolDownStore;
    private final IotActionExecutor executor;
    private final EnvironmentProperties properties;

    /** 룰 히트검사 -> 조치 결정 -> 장소 쿨 다운 추가 -> IOT 제어기기 명령 -> 결과 확인*/
    public ActionOutcome dispatch(SensorEvent event){
        SensorDetection detection = event.detection();

        //1. 룰 히트 이벤트인지 검사 룰 히트 외에는 모두 none()
        if(detection.type() != SensorEventType.RULE_HIT){
            return ActionOutcome.none();
        }


        //2. 조치 결정
        Optional<IotAction> resolved = IotActionPolicy.resolve(detection.measurement(), detection.operator());
        if(resolved.isEmpty()){
            return ActionOutcome.none(); //2-1. 정해지지 않은 측정항목, 연산자라면 none()
        }

        IotAction action = resolved.get();
        String location = detection.location();

        if(!coolDownStore.tryAcquire(cooldownKey(location, action), properties.coolDown())){
            return ActionOutcome.skipped(action); //2-2. cooldown에 걸린다면 skipped()
        }

        IotActionResult result = executor.execute(action, detection);

        if(!result.succeeded()){
            log.warn("조치 실패. location={}, action={}, error={}", location, action, result.error());
            return ActionOutcome.failed(action, failureReason(result), result.simulated()); //2-3 iot 통신 실패 혹은 통신은 성공했되 제어기 동작 실패 failed()
        }

        return ActionOutcome.confirm(action, result.at(), result.simulated(), null); //2-4 성공. notifiedAt은 아직 텔레그램 미구현으로 null
    }

    private String cooldownKey(String location, IotAction action){
        return COOLDOWN_KEY_PREFIX + location + ":" + action.name();
    }

    private String failureReason(IotActionResult result){
        if(!Objects.isNull(result.error())){
            return result.error();
        }

        return "제어기 동작 확인 실패 actioned=false";
    }
}
