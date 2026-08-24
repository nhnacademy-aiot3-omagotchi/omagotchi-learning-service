package site.omagotchi.learningservice.realtime.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.domain.CharacterAppearance;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.GameCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.realtime.application.PresenceCharacterSnapshot;
import site.omagotchi.learningservice.realtime.application.PresenceUserProfile;
import site.omagotchi.learningservice.realtime.application.PresenceUserProfileQuery;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaPresenceUserProfileQuery implements PresenceUserProfileQuery {

    private final UserCharacterRepository userCharacterRepository;
    private final GameCharacterRepository gameCharacterRepository;

    @Override
    public Map<UUID, PresenceUserProfile> findByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        var characters = userCharacterRepository.findByUserIdInAndRepresentativeTrue(userIds);
        Map<Long, GameCharacter> gameCharacters = gameCharacterRepository.findAllById(
                        characters.stream().map(character -> character.getGameCharacterId()).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(GameCharacter::getId, Function.identity()));

        return characters.stream()
                .filter(character -> gameCharacters.containsKey(character.getGameCharacterId()))
                .collect(Collectors.toMap(
                        character -> character.getUserId(),
                        character -> {
                            GameCharacter gameCharacter = gameCharacters.get(character.getGameCharacterId());
                            return new PresenceUserProfile(
                                    character.getNickname(),
                                    new PresenceCharacterSnapshot(
                                            gameCharacter.getAssetKey(),
                                            character.getColorId(),
                                            CharacterAppearance.assetKey(
                                                    gameCharacter.getAssetKey(),
                                                    character.getColorId()
                                            )
                                    )
                            );
                        }
                ));
    }
}
