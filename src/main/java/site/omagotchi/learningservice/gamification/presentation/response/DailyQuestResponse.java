package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.QuestType;

import java.time.LocalDate;

public record DailyQuestResponse(
        Long id,
        LocalDate questDate,
        QuestType type,
        String code,
        String title,
        int targetCount,
        int progressCount,
        long rewardXp,
        QuestStatus status
) {

    public static DailyQuestResponse from(DailyQuestResult result) {
        return new DailyQuestResponse(
                result.id(),
                result.questDate(),
                result.type(),
                result.code(),
                result.title(),
                result.targetCount(),
                result.progressCount(),
                result.rewardXp(),
                result.status()
        );
    }
}
