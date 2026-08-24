package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.GamificationProgressionResult;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
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

    private final CohortAccessService cohortAccessService;
    private final StudyProgressionRepository studyProgressionRepository;
    private final UserDailyQuestRepository userDailyQuestRepository;
    private final DateTimeProvider dateTimeProvider;

    public GamificationProgressionResult getProgression(UUID userId, Long cohortId, LocalDate aggregationDate) {
        // 소속하지 않은 기수를 조회하면 학습 시간 0으로 200을 반환해, 같은 화면의
        // 랭킹(requireActiveStudentMembershipId)·출석(requireActiveMembershipId)과 응답이 갈린다.
        // 출석 조회와 동일한 기준으로 맞춰 미소속 요청을 여기에서 차단한다.
        cohortAccessService.requireActiveMembershipId(cohortId, userId);

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
