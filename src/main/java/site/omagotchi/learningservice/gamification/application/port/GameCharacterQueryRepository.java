package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.GameCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 게임 캐릭터 조회 경계.
 *
 * <p>단건 조회와 여러 캐릭터 조회의 저장소 구현은 infrastructure가 책임진다.
 * Application은 Spring Data의 {@code findAllById} 같은 기술 계약을 알 필요가 없다.</p>
 */
public interface GameCharacterQueryRepository {

    Optional<GameCharacter> findById(Long gameCharacterId);

    List<GameCharacter> findAllById(Collection<Long> gameCharacterIds);
}
