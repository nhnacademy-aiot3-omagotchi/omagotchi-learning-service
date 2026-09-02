package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudySpaceConditionResult;

import java.util.ArrayList;
import java.util.List;

public record StudySpaceConditionToolResponse(
        String status,
        List<SpaceConditionResponse> spaces
) {

    public record SpaceConditionResponse(
            Long spaceId,
            String spaceName,
            String spaceType,
            String usageStatus,
            Double co2,          // ppm, 없으면 null
            Double temperature,  // 섭씨, 없으면 null
            Double humidity,     // %, 없으면 null
            String measuredAt    // 값이 속한 시간대, 없으면 null
    ) {
    }

    public static StudySpaceConditionToolResponse from(StudySpaceConditionResult result) {
        List<SpaceConditionResponse> spaces = new ArrayList<>();
        for (StudySpaceConditionResult.SpaceCondition space : result.spaces()) {
            spaces.add(new SpaceConditionResponse(
                    space.spaceId(),
                    space.spaceName(),
                    space.spaceType(),
                    space.usageStatus(),
                    space.co2(),
                    space.temperature(),
                    space.humidity(),
                    space.measuredAt() == null ? null : space.measuredAt().toString()
            ));
        }
        return new StudySpaceConditionToolResponse(result.status().name(), spaces);
    }
}
