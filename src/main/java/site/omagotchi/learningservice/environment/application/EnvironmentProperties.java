package site.omagotchi.learningservice.environment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @param cache    캐시 속성
 * @param coolDown 한공간에 조치를 연속으로 취하기 방지하기위한 시간 텀 (DEFAULT=5분)
 * @param iot      iot 제어기 HTTP 연결 속성
 */
@ConfigurationProperties(prefix = "environment")
public record EnvironmentProperties(
        Cache cache,
        Duration coolDown,
        Iot iot
) {
    // 컴팩트 생성자 검증
    public EnvironmentProperties {
        if (Objects.isNull(coolDown) || coolDown.isNegative() || coolDown.isZero()) {
            coolDown = Duration.ofMinutes(5);
        }
    }

    /**
     * @param capacity  저장 용량 (DEFAULT=1000)
     * @param retention 보관 기간 (DEFAULT=7일)
     */
    public record Cache(
            int capacity,
            Duration retention
    ) {

        // 컴팩트 생성자 검증
        public Cache {
            if (capacity <= 0) {
                capacity = 1000;
            }

            if (Objects.isNull(retention) || retention.isNegative() || retention.isZero()) {
                retention = Duration.ofDays(7);
            }
        }
    }

    /**
     * iot제어기 통신 엔트포인트 및 설정
     *
     * @param endpoints      장소별 제어기 url 맵
     * @param secret         제어기가 검사할 공유 토큰
     * @param requestTimeout 응답 대기 상한
     */
    public record Iot(
            Map<String, String> endpoints,
            String secret,
            Duration requestTimeout
    ) {

        // 컴팩트 생성자 검증
        public Iot {
            if(Objects.isNull(endpoints)){
                endpoints = Map.of();
            }

            Map<String, String> cleaned = new HashMap<>();
            for(Map.Entry<String, String> entry : endpoints.entrySet()){ //baseURL이 비어있는경우는 맵에서 제거
                String location = entry.getKey();
                String baseUrl = entry.getValue();

                if(Objects.isNull(baseUrl) || baseUrl.isBlank()){
                    continue;
                }

                cleaned.put(location, baseUrl);
            }

            endpoints = Map.copyOf(cleaned);

            if (Objects.isNull(requestTimeout) || requestTimeout.isNegative() || requestTimeout.isZero()) {
                requestTimeout = Duration.ofSeconds(3);
            }
        }


        public String getBaseUrl(String location) {
            return endpoints.get(location);
        }
    }
}