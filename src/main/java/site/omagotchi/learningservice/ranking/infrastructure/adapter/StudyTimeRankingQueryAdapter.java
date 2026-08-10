package site.omagotchi.learningservice.ranking.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.ranking.application.port.StudyTimeRankingQueryPort;
import site.omagotchi.learningservice.ranking.application.result.StudyTimeRankingResult;
import site.omagotchi.learningservice.ranking.infrastructure.StudyTimeRankingRepository;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StudyTimeRankingQueryAdapter implements StudyTimeRankingQueryPort {

    private final StudyTimeRankingRepository studyTimeRankingRepository;

    @Override
    public List<StudyTimeRankingResult> findStudySeconds(Long cohortId, LocalDate startDate, LocalDate endDate) {
        return studyTimeRankingRepository.findStudySeconds(cohortId, startDate, endDate)
                .stream()
                .map(row -> new StudyTimeRankingResult(row.getUserId(), row.getStudySeconds()))
                .toList();
    }
}
