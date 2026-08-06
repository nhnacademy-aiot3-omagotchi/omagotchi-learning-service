package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.HomeResult;

import java.util.List;

public record HomeResponse(
        CharacterGrowthResponse growth,
        List<DailyQuestResponse> dailyQuests
) {

    public static HomeResponse from(HomeResult result) {
        return new HomeResponse(
                CharacterGrowthResponse.from(result.growth()),
                result.dailyQuests().stream()
                        .map(DailyQuestResponse::from)
                        .toList()
        );
    }
}
