package site.omagotchi.learningservice.environment.application.query;

import site.omagotchi.learningservice.environment.domain.EnvironmentErrorCode;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 정규화가 끝난 센서 이벤트 조회 조건.
 *
 * @param type 이벤트 종류 필터. 비어 있으면 전체
 * @param deviceEui 기기 필터. null이면 전체
 * @param from 수신 시각 하한. 기본값은 to로부터 24시간 전
 * @param to 수신 시각 상한. 기본값은 현재
 * @param page 0-based 페이지 번호
 * @param size 페이지 크기 (1~100)
 */
public record SensorEventQuery(
        SensorEventType type,
        String deviceEui,
        Instant from,
        Instant to,
        int page,
        int size
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);


    /** 컴팩트 생성자 — 검증되지 않은 인스턴스가 만들어지지 않게 막는다 */
    public SensorEventQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(EnvironmentErrorCode.INVALID_PAGE_REQUEST);
        }

        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessException(EnvironmentErrorCode.INVALID_PERIOD_REQUEST);
        }
    }

    /**
     * 컨트롤러가 받은 날것의 요청값을 정규화한다.
     *
     * @param currentInstant 기본 기간 계산 기준. 호출부가 Clock에서 꺼내 넘긴다
     */
    public static SensorEventQuery of(
            SensorEventType type,
            String deviceEui,
            Instant from,
            Instant to,
            Integer page,
            Integer size,
            Instant currentInstant
    ) {
        Instant resolvedTo = (to == null) ? currentInstant : to;
        Instant resolvedFrom = (from == null) ? resolvedTo.minus(DEFAULT_WINDOW) : from;

        return new SensorEventQuery(
                type,
                normalizeDeviceEui(deviceEui),
                resolvedFrom,
                resolvedTo,
                (page == null) ? DEFAULT_PAGE : page,
                (size == null) ? DEFAULT_SIZE : size
        );
    }

    /** 캐시에서 꺼낸 이벤트가 이 조건을 통과하는지 */
    public boolean matches(SensorEvent event) {
        if(!Objects.isNull(type) && type != event.detection().type()){
            return false;
        }

        return deviceEui == null || deviceEui.equals(event.detection().deviceEui());
    }

    /** page * size는 int끼리 곱하면 뒤집힌다 */
    public long offset() {
        return (long) page * size;
    }

    private static String normalizeDeviceEui(String deviceEui) {
        if (deviceEui == null) {
            return null;
        }

        String trimmed = deviceEui.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}