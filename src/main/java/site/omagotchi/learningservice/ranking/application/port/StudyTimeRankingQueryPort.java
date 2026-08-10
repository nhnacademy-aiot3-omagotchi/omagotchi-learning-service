package site.omagotchi.learningservice.ranking.application.port;

import site.omagotchi.learningservice.ranking.application.result.StudyTimeRankingResult;

import java.time.LocalDate;
import java.util.List;

public interface StudyTimeRankingQueryPort {

    List<StudyTimeRankingResult> findStudySeconds(
            Long cohortId,
            LocalDate startDate,
            LocalDate endDate
    );
}
