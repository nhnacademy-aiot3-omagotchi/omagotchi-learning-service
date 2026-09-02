package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.StudyProgressionQueryRepository;
import site.omagotchi.learningservice.gamification.application.port.UserDailyQuestQueryRepository;
import site.omagotchi.learningservice.gamification.application.result.GamificationProgressionResult;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.StudyProgressionCalculator;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;
import site.omagotchi.learningservice.gamification.domain.WeekdayStreakCalculator;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationProgressionService {

    private static final int STREAK_LOOKBACK_DAYS = 14;

    private final CohortAccessService cohortAccessService;
    private final StudyProgressionQueryRepository studyProgressionQueryRepository;
    private final UserDailyQuestQueryRepository userDailyQuestQueryRepository;
    private final DateTimeProvider dateTimeProvider;

    public GamificationProgressionResult getProgression(UUID userId, Long cohortId, LocalDate aggregationDate) {
        // 소속하지 않은 기수를 조회하면 학습 시간 0으로 200을 반환해, 같은 화면의
        // 랭킹(requireActiveStudentMembershipId)·출석(requireActiveMembershipId)과 응답이 갈린다.
        // 출석 조회와 동일한 기준으로 맞춰 미소속 요청을 여기에서 차단한다.
        cohortAccessService.requireActiveMembershipId(cohortId, userId);

        LocalDate targetDate = aggregationDate == null
                ? dateTimeProvider.currentAggregationDate()
                : aggregationDate;
        long studySeconds = studyProgressionQueryRepository.getDailyStudySeconds(userId, cohortId, targetDate);

        return new GamificationProgressionResult(
                targetDate,
                StudyProgressionCalculator.calculate(studySeconds),
                WeekdayStreakCalculator.calculate(targetDate, attendedDates(userId, targetDate))
        );
    }

    /**
     * 여러 사용자의 평일 연속 출석일을 한 번의 질의로 계산한다.
     *
     * <p>랭킹 상위권 인원마다 {@link #getProgression}을 부르면 인원수만큼 질의가 늘고
     * 소속 기수 검증까지 반복된다. 랭킹은 이미 기수 접근을 검증한 뒤에 호출되므로
     * 여기서는 출석 기록만 읽는다.
     *
     * <p>기록이 없는 사용자는 결과 Map에 0으로 남는다. 호출부가 빠진 키를 따로 다루지 않게 한다.
     */
    public Map<UUID, Integer> findWeekdayStreakDays(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // 중복 userId가 섞여 와도 질의 파라미터가 부풀지 않게 한 번 정리한다.
        Set<UUID> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }

        LocalDate baseDate = dateTimeProvider.currentAggregationDate();
        Map<UUID, Set<LocalDate>> attendedDatesByUser = userDailyQuestQueryRepository
                .findByUsersAndDateRangeAndCode(
                        distinctUserIds,
                        baseDate.minusDays(STREAK_LOOKBACK_DAYS),
                        baseDate,
                        DailyQuestService.ATTENDANCE_CODE
                )
                .stream()
                .filter(GamificationProgressionService::attended)
                .collect(Collectors.groupingBy(
                        quest -> quest.getUserId(),
                        Collectors.mapping(quest -> quest.getQuestDate(), Collectors.toUnmodifiableSet())
                ));

        Map<UUID, Integer> streakDaysByUser = new LinkedHashMap<>();
        for (UUID userId : distinctUserIds) {
            Set<LocalDate> attendedDates = attendedDatesByUser.getOrDefault(userId, Set.of());
            streakDaysByUser.put(
                    userId,
                    WeekdayStreakCalculator.calculate(baseDate, attendedDates).currentWeekdayStreakDays()
            );
        }
        return Collections.unmodifiableMap(streakDaysByUser);
    }

    private static boolean attended(UserDailyQuest quest) {
        return quest.getStatus() == QuestStatus.COMPLETED || quest.getStatus() == QuestStatus.CLAIMED;
    }

    private Set<LocalDate> attendedDates(UUID userId, LocalDate baseDate) {
        return userDailyQuestQueryRepository.findByUserAndDateRangeAndCode(
                        userId,
                        baseDate.minusDays(STREAK_LOOKBACK_DAYS),
                        baseDate,
                        DailyQuestService.ATTENDANCE_CODE
                )
                .stream()
                .filter(GamificationProgressionService::attended)
                .map(quest -> quest.getQuestDate())
                .collect(Collectors.toUnmodifiableSet());
    }
}
