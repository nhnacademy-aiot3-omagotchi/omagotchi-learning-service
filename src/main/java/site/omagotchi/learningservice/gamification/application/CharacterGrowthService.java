package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.CharacterGrowthResult;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.LevelPolicyRepository;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterWriteRepository;
import site.omagotchi.learningservice.gamification.domain.CharacterNicknameValidator;
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
    private final UserCharacterWriteRepository userCharacterWriteRepository;
    private final LevelPolicyRepository levelPolicyRepository;

    public UserCharacter requireRepresentativeCharacter(UUID userId) {
        return userCharacterRepository.findFirstByUserIdAndRepresentativeTrueOrderByIdAsc(userId)
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND));
    }

    /**
     * 대표 캐릭터의 닉네임을 변경한다.
     *
     * <p>닉네임은 UserCharacter가 소유하므로 중복 확인, 상태 변경, 유니크 위반 변환을
     * 이 서비스의 트랜잭션 경계 하나에서 처리한다. 다른 Feature는 이 메서드로만 호출한다.
     *
     * <p>사전 exists 확인은 정상 경로에서 친절한 오류를 주기 위한 것이고,
     * 확인과 반영 사이의 경합은 부분 유니크 인덱스와 저장소 구현이 막는다.
     *
     * @return 정규화되어 반영된 닉네임
     */
    @Transactional
    public String changeRepresentativeNickname(UUID userId, String nickname) {
        String normalizedNickname = normalizeNickname(nickname);
        UserCharacter character = requireRepresentativeCharacter(userId);

        if (userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrueAndIdNot(
                normalizedNickname,
                character.getId()
        )) {
            throw new BusinessException(GamificationErrorCode.DUPLICATE_NICKNAME);
        }

        character.updateNickname(normalizedNickname);
        // Dirty Checking은 커밋 시점에 UPDATE를 내보낸다. 그때는 이 트랜잭션 밖이라
        // 유니크 위반을 업무 오류로 바꿀 수 없으므로 반영을 여기로 당긴다.
        userCharacterWriteRepository.flushRepresentative(character);

        return character.getNickname();
    }

    private String normalizeNickname(String nickname) {
        try {
            return CharacterNicknameValidator.normalize(nickname);
        } catch (IllegalArgumentException exception) {
            // 도메인 검증 실패를 API 응답 계약이 있는 오류로 바꿔서 밖으로 내보냄
            throw new BusinessException(GamificationErrorCode.INVALID_CHARACTER_NICKNAME, exception);
        }
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
