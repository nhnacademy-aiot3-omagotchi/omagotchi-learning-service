package site.omagotchi.learningservice.ranking.application.result;

import java.util.UUID;

public record StudyTimeRankingResult(
        UUID userId,
        long studySeconds
) {
}
