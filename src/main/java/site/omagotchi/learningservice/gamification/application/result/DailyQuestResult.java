package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.QuestType;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.time.LocalDate;

public record DailyQuestResult(
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

    public static DailyQuestResult from(UserDailyQuest quest) {
        return new DailyQuestResult(
                quest.getId(),
                quest.getQuestDate(),
                quest.getType(),
                quest.getCode(),
                quest.getTitle(),
                quest.getTargetCount(),
                quest.getProgressCount(),
                quest.getRewardXp(),
                quest.getStatus()
        );
    }
}
