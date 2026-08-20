package site.omagotchi.learningservice.gamification.domain;

public record LevelState(
        int level,
        long totalXp,
        long currentLevelMinXp,
        long nextLevelMinXp,
        AdvancementStage advancementStage
) {

    public long currentLevelXp() {
        return Math.max(0, totalXp - currentLevelMinXp);
    }

    public long nextLevelRequiredXp() {
        if (level >= LevelCalculator.MAX_LEVEL) {
            return 0;
        }
        return nextLevelMinXp - currentLevelMinXp;
    }
}
