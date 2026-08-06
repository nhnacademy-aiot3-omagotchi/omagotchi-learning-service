package site.omagotchi.learningservice.gamification.application.result;

import java.util.List;

public record HomeResult(
        CharacterGrowthResult growth,
        List<DailyQuestResult> dailyQuests
) {
}
