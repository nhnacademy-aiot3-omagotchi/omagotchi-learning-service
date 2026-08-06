package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.GamificationProgressionResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.StudyProgressionCalculator;
import site.omagotchi.learningservice.gamification.domain.WeekdayStreakCalculator;
import site.omagotchi.learningservice.gamification.infrastructure.StudyProgressionRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserDailyQuestRepository;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationProgressionService {

    private static final int STREAK_LOOKBACK_DAYS = 14;

    private final StudyProgressionRepository studyProgressionRepository;
    private final UserDailyQuestRepository userDailyQuestRepository;
    private final DateTimeProvider dateTimeProvider;

    public GamificationProgressionResult getProgression(UUID userId, Long cohortId, LocalDate aggregationDate) {
        LocalDate targetDate = aggregationDate == null
                ? dateTimeProvider.currentAggregationDate()
                : aggregationDate;
        long studySeconds = studyProgressionRepository.getDailyStudySeconds(userId, cohortId, targetDate);

        return new GamificationProgressionResult(
                targetDate,
                StudyProgressionCalculator.calculate(studySeconds),
                WeekdayStreakCalculator.calculate(targetDate, attendedDates(userId, targetDate))
        );
    }

    private Set<LocalDate> attendedDates(UUID userId, LocalDate baseDate) {
        return userDailyQuestRepository.findByUserIdAndQuestDateBetweenAndCodeOrderByQuestDateAsc(
                        userId,
                        baseDate.minusDays(STREAK_LOOKBACK_DAYS),
                        baseDate,
                        DailyQuestService.ATTENDANCE_CODE
                )
                .stream()
                .filter(quest -> quest.getStatus() == QuestStatus.COMPLETED || quest.getStatus() == QuestStatus.CLAIMED)
                .map(quest -> quest.getQuestDate())
                .collect(Collectors.toUnmodifiableSet());
    }
}
