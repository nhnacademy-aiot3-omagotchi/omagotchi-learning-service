package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;

import java.util.List;
import java.util.Optional;

public interface GameCharacterRepository extends JpaRepository<GameCharacter, Long> {

    Optional<GameCharacter> findByIdAndActiveTrue(Long id);

    List<GameCharacter> findByActiveTrueOrderByIdAsc();
}
