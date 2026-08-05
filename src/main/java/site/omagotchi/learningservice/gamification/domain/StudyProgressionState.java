package site.omagotchi.learningservice.gamification.domain;

public record StudyProgressionState(
        long studySeconds, // 공부 시간
        boolean reachedFourHours, // 4시간 도달
        boolean reachedSixHours, // 6시간 도달
        boolean reachedEightHours // 8시간 도달
) {
}
