package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.application.result.GameCharacterResult;
import site.omagotchi.learningservice.gamification.application.result.UserCharacterResult;
import site.omagotchi.learningservice.gamification.domain.CharacterNicknameValidator;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.GameCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterOnboardingService {

    private final GameCharacterRepository gameCharacterRepository;
    private final UserCharacterRepository userCharacterRepository;

    public List<GameCharacterResult> getAvailableCharacters() {
        return gameCharacterRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(GameCharacterResult::from)
                .toList();
    }

    @Transactional
    public UserCharacterResult createRepresentativeCharacter(UUID userId, CreateUserCharacterCommand command) {
        // 성장 상태는 대표 캐릭터 하나를 기준으로 잡아서 온보딩 중복 생성을 먼저 끊음
        if (userCharacterRepository.existsByUserIdAndRepresentativeTrue(userId)) {
            throw new BusinessException(GamificationErrorCode.REPRESENTATIVE_CHARACTER_ALREADY_EXISTS);
        }
        GameCharacter gameCharacter = gameCharacterRepository.findByIdAndActiveTrue(command.gameCharacterId())
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.GAME_CHARACTER_NOT_FOUND));

        String nickname = normalizeNickname(command.nickname());
        UserCharacter userCharacter = userCharacterRepository.save(UserCharacter.representative(
                userId,
                gameCharacter.getId(),
                nickname
        ));
        return UserCharacterResult.from(userCharacter, gameCharacter);
    }

    private String normalizeNickname(String nickname) {
        try {
            return CharacterNicknameValidator.normalize(nickname);
        } catch (IllegalArgumentException exception) {
            // 도메인 검증 실패를 API 응답 계약이 있는 오류로 바꿔서 밖으로 내보냄
            throw new BusinessException(GamificationErrorCode.INVALID_CHARACTER_NICKNAME, exception);
        }
    }
}
