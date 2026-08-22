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
import site.omagotchi.learningservice.gamification.application.port.UserCharacterWriteRepository;
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

    @Mock
    private UserCharacterWriteRepository userCharacterWriteRepository;

    @Test
    @DisplayName("별명을 trim해서 대표 캐릭터를 생성한다")
    void createsRepresentativeCharacterWithNormalizedNickname() {
        GameCharacter gameCharacter = GameCharacter.create("NIGHT_CLASS", "야간반", "기본 캐릭터", "night");
        ReflectionTestUtils.setField(gameCharacter, "id", 1L);
        when(gameCharacterRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(gameCharacter));
        when(userCharacterRepository.existsByUserIdAndRepresentativeTrue(USER_ID)).thenReturn(false);
        when(userCharacterWriteRepository.saveRepresentative(any(UserCharacter.class))).thenAnswer(invocation -> {
            UserCharacter character = invocation.getArgument(0);
            ReflectionTestUtils.setField(character, "id", 10L);
            return character;
        });
        CharacterOnboardingService service = new CharacterOnboardingService(
                gameCharacterRepository,
                userCharacterRepository,
                userCharacterWriteRepository
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
                userCharacterRepository,
                userCharacterWriteRepository
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

    /**
     * 사전 exists 확인과 저장 사이에 다른 요청이 같은 닉네임을 선점하면
     * 저장 시점에 부분 유니크 인덱스가 위반된다. 저장소 구현이 이를
     * DUPLICATE_NICKNAME으로 바꿔서 던지므로, 서비스는 그대로 밖으로 내보내야 한다.
     * 여기서 원본 예외가 새면 사용자는 500과 "일시적인 오류입니다"를 보게 된다.
     */
    @Test
    @DisplayName("사전 확인 통과 후 저장 경합이 나면 DUPLICATE_NICKNAME이 그대로 전달된다")
    void propagatesDuplicateNicknameFromSaveRace() {
        GameCharacter gameCharacter = GameCharacter.create("NIGHT_CLASS", "야간반", "기본 캐릭터", "night");
        ReflectionTestUtils.setField(gameCharacter, "id", 1L);
        when(gameCharacterRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(gameCharacter));
        when(userCharacterRepository.existsByUserIdAndRepresentativeTrue(USER_ID)).thenReturn(false);
        when(userCharacterRepository.existsByNicknameIgnoreCaseAndRepresentativeTrue("야간반장"))
                .thenReturn(false);
        when(userCharacterWriteRepository.saveRepresentative(any(UserCharacter.class)))
                .thenThrow(new site.omagotchi.learningservice.global.exception.BusinessException(
                        GamificationErrorCode.DUPLICATE_NICKNAME
                ));
        CharacterOnboardingService service = new CharacterOnboardingService(
                gameCharacterRepository,
                userCharacterRepository,
                userCharacterWriteRepository
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
