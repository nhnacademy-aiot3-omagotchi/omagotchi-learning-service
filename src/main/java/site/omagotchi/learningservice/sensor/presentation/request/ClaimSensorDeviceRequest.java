package site.omagotchi.learningservice.sensor.presentation.request;

import jakarta.validation.constraints.NotNull;

/**
 * 센서 인계 요청.
 *
 * <p>배치할 공간만 받는다. 표시명·설치 지점·수집 주기는 이전 기수가 쓰던 값을 그대로
 * 잇는다 — 같은 자리에 붙어 있는 같은 장비이므로 다시 입력받을 이유가 없다.</p>
 */
public record ClaimSensorDeviceRequest(
        @NotNull
        Long spaceId
) {
}
