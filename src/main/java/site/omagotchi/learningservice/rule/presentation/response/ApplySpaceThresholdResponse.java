package site.omagotchi.learningservice.rule.presentation.response;

import site.omagotchi.learningservice.rule.application.result.ApplySpaceThresholdResult;

/**
 * 일괄 적용 결과.
 *
 * @param applied 실제로 값이 바뀐 룰 수
 * @param unchanged 이미 같은 값이라 건드리지 않은 룰 수
 * @param missing 룰이 없어 건너뛴 (기기 × metric) 수. 0 이 아니면 화면이 알려야 한다
 */
public record ApplySpaceThresholdResponse(
        Long spaceId,
        int deviceCount,
        int applied,
        int unchanged,
        int missing
) {

    public static ApplySpaceThresholdResponse from(ApplySpaceThresholdResult result) {
        return new ApplySpaceThresholdResponse(
                result.spaceId(),
                result.deviceCount(),
                result.applied(),
                result.unchanged(),
                result.missing()
        );
    }
}
