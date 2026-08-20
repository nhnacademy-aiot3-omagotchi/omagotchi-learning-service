package site.omagotchi.learningservice.environment.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.environment.application.port.ActionCoolDownStore;

import java.time.Duration;
/**
 * redis에서 조회를 통해 cooldown할지 여부를 결정
 *
 * <p/>
 * 쿨다운의 역할(아래 두가지 상황을 예방)
 *
 * <p/>
 * 1. 한 공간안에 센서가 여러개있을 수 있지만 각 센서가 다른 타이밍에 룰 히트되면 iot기기가 두번 동작할 수 있음.
 * 2. 한 공간안에 하나의 센서가 연속된 룰 히트가 발생될때 iot기기가 두번 동작할 수 있음.
 */
@RequiredArgsConstructor
@Repository
public class RedisActionCooldownStore implements ActionCoolDownStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, Duration coolDown) {

        //키가 등록되어있다면 이미 iot기기가 작동했다는 뜻(실패했을수있지만). 그렇다면 coolDown 시간동안 false를 반환
        //key는 location, value는 암거나 넣음. 키의 존재만 확인하면 되니까
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key,"", coolDown);

        return Boolean.TRUE.equals(acquired);
    }
}
