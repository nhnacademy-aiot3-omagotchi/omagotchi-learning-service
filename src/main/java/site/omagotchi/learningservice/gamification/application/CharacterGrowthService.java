package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.CharacterGrowthResult;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.gamification.domain.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.LevelPolicyRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterGrowthService {

    private final UserCharacterRepository userCharacterRepository;
    private final LevelPolicyRepository levelPolicyRepository;

    public UserCharacter requireRepresentativeCharacter(UUID userId) {
        return userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId)
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND));
    }

    public Optional<RepresentativeCharacterResult> findRepresentativeCharacter(UUID userId) {
        return userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId)
                .map(RepresentativeCharacterResult::from);
    }

    public List<RepresentativeCharacterResult> findRepresentativeCharacters(Collection<UUID> userIds) {
        return userCharacterRepository.findByUserIdInAndRepresentativeTrue(userIds)
                .stream()
                .map(RepresentativeCharacterResult::from)
                .toList();
    }

    public CharacterGrowthResult getGrowth(UUID userId) {
        UserCharacter character = requireRepresentativeCharacter(userId);
        List<LevelPolicy> policies = requireLevelPolicies();
        return CharacterGrowthResult.from(character, character.levelState(policies));
    }

    List<LevelPolicy> requireLevelPolicies() {
        List<LevelPolicy> policies = levelPolicyRepository.findByLevelLessThanEqualOrderByLevelAsc(30);
        if (policies.isEmpty()) {
            throw new BusinessException(GamificationErrorCode.LEVEL_POLICY_NOT_FOUND);
        }
        return policies;
    }
}
