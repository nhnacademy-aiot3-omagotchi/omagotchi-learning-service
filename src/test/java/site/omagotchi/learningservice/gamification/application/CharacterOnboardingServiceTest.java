package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.GameCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("캐릭터 온보딩 서비스")
class CharacterOnboardingServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private GameCharacterRepository gameCharacterRepository;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Test
    @DisplayName("별명을 trim해서 대표 캐릭터를 생성한다")
    void createsRepresentativeCharacterWithNormalizedNickname() {
        GameCharacter gameCharacter = GameCharacter.create("NIGHT_CLASS", "야간반", "기본 캐릭터", "night");
        ReflectionTestUtils.setField(gameCharacter, "id", 1L);
        when(gameCharacterRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(gameCharacter));
        when(userCharacterRepository.existsByUserIdAndRepresentativeTrue(USER_ID)).thenReturn(false);
        when(userCharacterRepository.save(any(UserCharacter.class))).thenAnswer(invocation -> {
            UserCharacter character = invocation.getArgument(0);
            ReflectionTestUtils.setField(character, "id", 10L);
            return character;
        });
        CharacterOnboardingService service = new CharacterOnboardingService(
                gameCharacterRepository,
                userCharacterRepository
        );

        var result = service.createRepresentativeCharacter(
                USER_ID,
                new CreateUserCharacterCommand(1L, "  야간반장  ", "pistachio")
        );

        assertAll(
                () -> assertEquals("야간반장", result.nickname()),
                () -> assertEquals("야간반장", result.displayName()),
                () -> assertEquals("NIGHT_CLASS", result.gameCharacterCode()),
                () -> assertEquals("night", result.type()),
                () -> assertEquals("pistachio", result.colorId()),
                () -> assertEquals("night/pistachio", result.assetKey()),
                () -> assertEquals("야간반", result.gameCharacterName()),
                () -> assertEquals(0, result.totalXp()),
                () -> assertEquals(1, result.level()),
                () -> assertEquals(AdvancementStage.BASE, result.advancementStage()),
                () -> assertEquals(true, result.representative())
        );
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 대표 캐릭터를 만들 수 없다")
    void rejectsDuplicateNickname() {
        GameCharacter gameCharacter = GameCharacter.create("NIGHT_CLASS", "야간반", "기본 캐릭터", "night");
        ReflectionTestUtils.setField(gameCharacter, "id", 1L);
        when(gameCharacterRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(gameCharacter));
        when(userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrue("야간반장"))
                .thenReturn(true);
        CharacterOnboardingService service = new CharacterOnboardingService(
                gameCharacterRepository,
                userCharacterRepository
        );

        var exception = assertThrows(
                site.omagotchi.learningservice.global.exception.BusinessException.class,
                () -> service.createRepresentativeCharacter(
                        USER_ID,
                        new CreateUserCharacterCommand(1L, "야간반장", "pistachio")
                )
        );

        assertSame(GamificationErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
    }
}
