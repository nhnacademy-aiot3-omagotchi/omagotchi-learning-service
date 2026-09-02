package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.UserDailyQuestQueryRepository;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 일일 퀘스트 조회의 Spring Data JPA 구현. */
@Repository
@RequiredArgsConstructor
public class UserDailyQuestJpaPersistence implements UserDailyQuestQueryRepository {

    private final UserDailyQuestRepository userDailyQuestRepository;

    @Override
    public List<UserDailyQuest> findByUserAndDateRangeAndCode(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            String code
    ) {
        return userDailyQuestRepository.findByUserIdAndQuestDateBetweenAndCodeOrderByQuestDateAsc(
                userId,
                startDate,
                endDate,
                code
        );
    }

    @Override
    public List<UserDailyQuest> findByUsersAndDateRangeAndCode(
            Collection<UUID> userIds,
            LocalDate startDate,
            LocalDate endDate,
            String code
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userDailyQuestRepository.findByUserIdInAndQuestDateBetweenAndCode(
                userIds,
                startDate,
                endDate,
                code
        );
    }
}
