package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 시간대·공간 블록으로 쪼갠 학습 환경 분석의 LLM 전달용 모양.
 */
public record StudyEnvironmentToolResponse(
        String status,
        int periodDays,
        String spaceSource,
        int analyzedSessionCount,
        int unknownSpaceSessionCount,
        List<BlockSummaryResponse> timeBands,
        List<BlockSummaryResponse> spaces
) {

    public record BlockSummaryResponse(
            String label,
            Long spaceId,
            int sessionCount,
            long studyMinutes,
            long spanMinutes,          // 자리에 있던 시간
            int densityPercent,        // 공부 시간 ÷ 자리에 있던 시간
            Double averageCo2,         // ppm, 없으면 null
            Double averageTemperature, // 섭씨, 없으면 null
            Double averageHumidity     // %, 없으면 null
    ) {
    }

    public static StudyEnvironmentToolResponse from(StudyEnvironmentResult result) {
        return new StudyEnvironmentToolResponse(
                result.status().name(),
                result.periodDays(),
                result.spaceSource(),
                result.analyzedSessionCount(),
                result.unknownSpaceSessionCount(),
                convert(result.timeBands()),
                convert(result.spaces())
        );
    }

    private static List<BlockSummaryResponse> convert(
            List<StudyEnvironmentResult.BlockSummary> blocks
    ) {
        List<BlockSummaryResponse> converted = new ArrayList<>();
        for (StudyEnvironmentResult.BlockSummary block : blocks) {
            converted.add(new BlockSummaryResponse(
                    block.label(),
                    block.spaceId(),
                    block.sessionCount(),
                    block.studyMinutes(),
                    block.spanMinutes(),
                    block.densityPercent(),
                    block.averageCo2(),
                    block.averageTemperature(),
                    block.averageHumidity()
            ));
        }
        return converted;
    }
}
