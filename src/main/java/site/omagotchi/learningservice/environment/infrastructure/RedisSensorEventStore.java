package site.omagotchi.learningservice.environment.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.environment.application.EnvironmentProperties;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 뷰에 보여줄 센서 이벤트 저장(레디스)
 *
 * sensorEvent = SensorDetection(어떤일) + ActionOutcome(조치)
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class RedisSensorEventStore implements SensorEventStore {
    private static final String KEY = "omagotchi:sensor:events";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final EnvironmentProperties properties;
    private final Clock clock;

    /**
     * 레디스 캐시 저장
     * <p/>
     *
     * ZSET을 사용해 중복 제거, score에 따른 정렬,삭제 가능
     * */
    @Override
    public void save(SensorEvent event) {
        String json = jsonMapper.writeValueAsString(event);
        long score = event.detection().receivedAt().toEpochMilli(); // 수신 시각을 점수화

        Duration retention = properties.cache().retention();
        long expiredBefore = clock.instant().minus(retention).toEpochMilli();
        long capacity = properties.cache().capacity();

        // 1. 적재
        redisTemplate.opsForZSet().add(KEY, json, score);

        // 2. 시간 기준 - retention 보다 오래된 멤버 제거
        redisTemplate.opsForZSet().removeRangeByScore(KEY, Double.NEGATIVE_INFINITY, expiredBefore);

        // 3. 개수 기준 - 최대 개수를 넘었을때 오래된 멤버부터 제거
        redisTemplate.opsForZSet().removeRange(KEY, 0 , -(capacity + 1));

        // 4. 키 자체 ttl 설정 - 데이터가 계속들어오는 한 갱신됨
        redisTemplate.expire(KEY, retention);
    }

    /**
     * 레디스 캐시 조회
     * <p/>
     *
     * 수신 시각을 기준으로 범위안에있는 이벤트들을 추출
     * */
    @Override
    public List<SensorEvent> findByReceivedAt(Instant from, Instant to) {
        Set<String> members = redisTemplate.opsForZSet()
                .reverseRangeByScore(KEY, from.toEpochMilli(), to.toEpochMilli());

        if(Objects.isNull(members) || members.isEmpty()){
            return List.of();
        }


        List<SensorEvent> sensorEvents = new ArrayList<>();

        for(String member : members){
            SensorEvent sensorEvent = read(member);

            if(Objects.isNull(sensorEvent)){
                continue;
            }

            sensorEvents.add(sensorEvent);
        }

        return sensorEvents;
    }

    /** 역직렬화 내부 메서드 */
    private SensorEvent read(String json){
        try{
            return jsonMapper.readValue(json, SensorEvent.class);
        }catch (RuntimeException e){
            log.warn("센서 이벤트 역직렬화 실패. 건넘뜀: {}", json, e);
            return null;
        }
    }
}