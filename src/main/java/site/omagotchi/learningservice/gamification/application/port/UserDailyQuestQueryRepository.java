package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 일일 퀘스트 조회 경계.
 *
 * <p>날짜 범위와 퀘스트 코드는 Application의 조회 조건으로 표현하되,
 * Spring Data 메서드 이름과 repository 타입은 infrastructure에 숨긴다.</p>
 */
public interface UserDailyQuestQueryRepository {

    List<UserDailyQuest> findByUserAndDateRangeAndCode(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            String code
    );

    List<UserDailyQuest> findByUsersAndDateRangeAndCode(
            Collection<UUID> userIds,
            LocalDate startDate,
            LocalDate endDate,
            String code
    );
}
