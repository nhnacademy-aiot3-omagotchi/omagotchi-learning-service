package site.omagotchi.learningservice.environment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
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

        if (Objects.isNull(cache)) {
            cache = new Cache(0, null);
        }

        if (Objects.isNull(coolDown) || coolDown.isNegative() || coolDown.isZero()) {
            coolDown = Duration.ofMinutes(5);
        }

        if (Objects.isNull(iot)) {
            iot = new Iot(null, null, null);
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
     * iot 제어기 통신 설정.
     *
     * <p>제어기가 하나인 전제다. 시뮬레이터 한 대가 모든 방을 처리하고 location은 요청 본문으로
     * 전달된다. 실물 보드가 2대 이상 붙으면 location → baseUrl 매핑으로 넓힌다.</p>
     *
     * @param baseUrl        제어기 주소. 비어 있으면 조치하지 않는다
     * @param secret         제어기가 검사할 공유 토큰
     * @param requestTimeout 응답 대기 상한 (DEFAULT=3초). MQ 리스너 스레드를 붙잡는 시간이다
     */
    public record Iot(
            String baseUrl,
            String secret,
            Duration requestTimeout
    ) {

        // 컴팩트 생성자 검증
        public Iot {
            if (Objects.isNull(requestTimeout) || requestTimeout.isNegative() || requestTimeout.isZero()) {
                requestTimeout = Duration.ofSeconds(3);
            }
        }

        public boolean configured() {
            return (Objects.nonNull(baseUrl) && !baseUrl.isBlank()) || (Objects.nonNull(secret) && !secret.isBlank());
        }
    }
}