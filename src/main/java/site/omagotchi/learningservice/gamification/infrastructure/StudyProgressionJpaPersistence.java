package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.StudyProgressionQueryRepository;

import java.time.LocalDate;
import java.util.UUID;

/** 학습 진행도 조회의 Spring Data JPA 구현. */
@Repository
@RequiredArgsConstructor
public class StudyProgressionJpaPersistence implements StudyProgressionQueryRepository {

    private final StudyProgressionRepository studyProgressionRepository;

    @Override
    public long getDailyStudySeconds(UUID userId, Long cohortId, LocalDate aggregationDate) {
        return studyProgressionRepository.getDailyStudySeconds(userId, cohortId, aggregationDate);
    }
}
