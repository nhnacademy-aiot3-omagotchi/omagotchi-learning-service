package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.StudyEnvironmentResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 학습 세션을 공간·환경과 묶어 본 결과의 LLM 전달용 모양.
 */
public record StudyEnvironmentToolResponse(
        String status,
        int periodDays,
        int analyzedSessionCount,
        int unknownSpaceSessionCount,
        List<SpacePerformanceResponse> spaces,
        List<MeasurementContrastResponse> contrasts
) {

    public record SpacePerformanceResponse(
            Long spaceId,
            String spaceName,
            int sessionCount,
            long totalStudyMinutes,
            int focusDensityPercent,
            Double averageCo2,          // ppm, 없으면 null
            Double averageTemperature,  // 섭씨, 없으면 null
            Double averageHumidity      // %, 없으면 null
    ) {
    }

    public record MeasurementContrastResponse(
            String measurement,
            double medianValue,
            int lowSessionCount,
            int lowFocusDensityPercent,
            double lowAverageValue,
            int highSessionCount,
            int highFocusDensityPercent,
            double highAverageValue
    ) {
    }

    public static StudyEnvironmentToolResponse from(StudyEnvironmentResult result) {
        List<SpacePerformanceResponse> spaces = new ArrayList<>();
        for (StudyEnvironmentResult.SpacePerformance space : result.spaces()) {
            spaces.add(new SpacePerformanceResponse(
                    space.spaceId(),
                    space.spaceName(),
                    space.sessionCount(),
                    space.totalStudyMinutes(),
                    space.focusDensityPercent(),
                    space.averageCo2(),
                    space.averageTemperature(),
                    space.averageHumidity()
            ));
        }

        List<MeasurementContrastResponse> contrasts = new ArrayList<>();
        for (StudyEnvironmentResult.MeasurementContrast contrast : result.contrasts()) {
            contrasts.add(new MeasurementContrastResponse(
                    contrast.measurement(),
                    contrast.medianValue(),
                    contrast.lowSessionCount(),
                    contrast.lowFocusDensityPercent(),
                    contrast.lowAverageValue(),
                    contrast.highSessionCount(),
                    contrast.highFocusDensityPercent(),
                    contrast.highAverageValue()
            ));
        }

        return new StudyEnvironmentToolResponse(
                result.status().name(),
                result.periodDays(),
                result.analyzedSessionCount(),
                result.unknownSpaceSessionCount(),
                spaces,
                contrasts
        );
    }
}
