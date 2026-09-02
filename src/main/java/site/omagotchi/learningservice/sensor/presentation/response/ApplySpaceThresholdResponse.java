package site.omagotchi.learningservice.sensor.presentation.response;

import site.omagotchi.learningservice.sensor.application.result.ApplySpaceThresholdResult;

/**
 * 일괄 적용 결과.
 *
 * @param created 이번 요청으로 새로 만든 룰 수
 * @param applied 기존 룰 중 실제로 값이 바뀐 수
 * @param unchanged 이미 같은 값이라 건드리지 않은 룰 수
 * @param missing 하위 호환을 위해 유지한 값. 공간 일괄 저장은 없는 룰도 생성하므로 항상 0이다
 */
public record ApplySpaceThresholdResponse(
        Long spaceId,
        int deviceCount,
        int created,
        int applied,
        int unchanged,
        int missing
) {

    public static ApplySpaceThresholdResponse from(ApplySpaceThresholdResult result) {
        return new ApplySpaceThresholdResponse(
                result.spaceId(),
                result.deviceCount(),
                result.created(),
                result.applied(),
                result.unchanged(),
                result.missing()
        );
    }
}
