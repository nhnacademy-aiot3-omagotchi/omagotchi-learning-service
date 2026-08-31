package site.omagotchi.learningservice.gamification.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 유저 일일 퀘스트
 */
public interface UserDailyQuestRepository extends JpaRepository<UserDailyQuest, Long> {

    boolean existsByUserIdAndQuestDate(UUID userId, LocalDate questDate);

    List<UserDailyQuest> findByUserIdAndQuestDateOrderByIdAsc(UUID userId, LocalDate questDate);

    Optional<UserDailyQuest> findByUserIdAndQuestDateAndCode(UUID userId, LocalDate questDate, String code);

    List<UserDailyQuest> findByUserIdAndQuestDateBetweenAndCodeOrderByQuestDateAsc(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            String code
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from UserDailyQuest q where q.id = :id")
    Optional<UserDailyQuest> findWithLockById(Long id);
}
