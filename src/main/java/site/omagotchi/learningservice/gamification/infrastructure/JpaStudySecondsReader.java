package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.application.port.StudySecondsReader;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaStudySecondsReader implements StudySecondsReader {

    private final StudyProgressionRepository studyProgressionRepository;

    @Override
    public long dailyStudySeconds(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studyProgressionRepository.getDailyStudySeconds(userId, cohortId, aggregationDate);
    }

    @Override
    public long recentAttendedAverageSeconds(UUID userId, Long cohortId, LocalDate aggregationDate) {
        // 평균은 소수로 나오므로 초 단위로 반올림해 application에 넘긴다.
        double average = studyProgressionRepository.getRecentAttendedAverageStudySeconds(
                userId, cohortId, aggregationDate
        );
        return Math.round(average);
    }

    @Override
    public boolean hasStudyRecordBefore(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studyProgressionRepository.existsStudyRecordBefore(userId, cohortId, aggregationDate);
    }
}
