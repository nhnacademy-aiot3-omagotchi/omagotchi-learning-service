package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.GameCharacterQueryRepository;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 게임 캐릭터 조회의 Spring Data JPA 구현. */
@Repository
@RequiredArgsConstructor
public class GameCharacterJpaPersistence implements GameCharacterQueryRepository {

    private final GameCharacterRepository gameCharacterRepository;

    @Override
    public Optional<GameCharacter> findById(Long gameCharacterId) {
        return gameCharacterRepository.findById(gameCharacterId);
    }

    @Override
    public List<GameCharacter> findAllById(Collection<Long> gameCharacterIds) {
        if (gameCharacterIds == null || gameCharacterIds.isEmpty()) {
            return List.of();
        }
        return gameCharacterRepository.findAllById(gameCharacterIds);
    }
}
