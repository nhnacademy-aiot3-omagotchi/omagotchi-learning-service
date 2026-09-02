package site.omagotchi.learningservice.gamification.application.port;

import java.time.LocalDate;
import java.util.UUID;

/** 학습 진행도 조회 경계. */
public interface StudyProgressionQueryRepository {

    long getDailyStudySeconds(
            UUID userId,
            Long cohortId,
            LocalDate aggregationDate
    );
}
