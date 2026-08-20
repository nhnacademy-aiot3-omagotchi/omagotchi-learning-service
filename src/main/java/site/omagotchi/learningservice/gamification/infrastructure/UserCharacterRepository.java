package site.omagotchi.learningservice.gamification.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 캐릭터
 */
public interface UserCharacterRepository extends JpaRepository<UserCharacter, Long> {

    Optional<UserCharacter> findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(UUID userId);

    boolean existsByUserIdAndRepresentativeTrue(UUID userId);

    boolean existsByNicknameIgnoreCaseAndRepresentativeTrue(String nickname);

    boolean existsByNicknameIgnoreCaseAndRepresentativeTrueAndIdNot(String nickname, Long id);

    List<UserCharacter> findByUserIdInAndRepresentativeTrue(Collection<UUID> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserCharacter c where c.id = :id")
    Optional<UserCharacter> findWithLockById(Long id);
}
