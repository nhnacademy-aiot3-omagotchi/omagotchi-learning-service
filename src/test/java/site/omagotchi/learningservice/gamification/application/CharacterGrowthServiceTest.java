package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterWriteRepository;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.LevelPolicyRepository;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterQueryRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 닉네임 규칙의 소유자는 Gamification이다.
 * 정규화·중복·금칙어 판정과 유니크 위반 전파를 여기에서 검증한다.
 */
@DisplayName("캐릭터 성장 서비스")
@ExtendWith(MockitoExtension.class)
class CharacterGrowthServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long CHARACTER_ID = 30L;

    @Mock
    private UserCharacterQueryRepository userCharacterQueryRepository;

    @Mock
    private UserCharacterWriteRepository userCharacterWriteRepository;

    @Mock
    private LevelPolicyRepository levelPolicyRepository;

    @InjectMocks
    private CharacterGrowthService characterGrowthService;

    @Test
    @DisplayName("닉네임을 trim해서 대표 캐릭터 별명을 변경한다")
    void changesNicknameWithNormalization() {
        UserCharacter character = representativeCharacter("오마");
        given(userCharacterQueryRepository.findRepresentativeByUserId(USER_ID))
                .willReturn(Optional.of(character));
        given(userCharacterQueryRepository.existsRepresentativeByNicknameExcludingId(
                "새이름", CHARACTER_ID
        )).willReturn(false);

        String changed = characterGrowthService.changeRepresentativeNickname(USER_ID, "  새이름  ");

        assertAll(
                () -> assertEquals("새이름", changed),
                () -> assertEquals("새이름", character.getNickname())
        );
        // Dirty Checking에만 맡기면 커밋 시점에 UPDATE가 나가 유니크 위반을 잡을 수 없다.
        verify(userCharacterWriteRepository).flushRepresentative(character);
    }

    @Test
    @DisplayName("닉네임은 2~12자로 제한한다")
    void rejectsInvalidNicknameLength() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> characterGrowthService.changeRepresentativeNickname(USER_ID, " a ")
        );

        assertSame(GamificationErrorCode.INVALID_CHARACTER_NICKNAME, exception.getErrorCode());
        // 형식이 틀리면 조회도 반영도 하지 않는다.
        verify(userCharacterWriteRepository, never()).flushRepresentative(any());
    }

    @Test
    @DisplayName("금칙어가 포함된 닉네임은 변경할 수 없다")
    void rejectsForbiddenNickname() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> characterGrowthService.changeRepresentativeNickname(USER_ID, "시1발")
        );

        assertSame(GamificationErrorCode.INVALID_CHARACTER_NICKNAME, exception.getErrorCode());
        verify(userCharacterWriteRepository, never()).flushRepresentative(any());
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임은 변경할 수 없다")
    void rejectsDuplicateNickname() {
        UserCharacter character = representativeCharacter("오마");
        given(userCharacterQueryRepository.findRepresentativeByUserId(USER_ID))
                .willReturn(Optional.of(character));
        given(userCharacterQueryRepository.existsRepresentativeByNicknameExcludingId(
                "새이름", CHARACTER_ID
        )).willReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> characterGrowthService.changeRepresentativeNickname(USER_ID, "새이름")
        );

        assertSame(GamificationErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
        verify(userCharacterWriteRepository, never()).flushRepresentative(any());
    }

    /**
     * 사전 exists 확인과 반영 사이에 다른 요청이 같은 닉네임을 선점하면
     * 저장소 구현이 유니크 위반을 DUPLICATE_NICKNAME으로 바꿔 던진다.
     * 여기서 원본 예외가 새면 사용자는 500과 "일시적인 오류입니다"를 보게 된다.
     */
    @Test
    @DisplayName("사전 확인 통과 후 반영 경합이 나면 DUPLICATE_NICKNAME이 그대로 전달된다")
    void propagatesDuplicateNicknameFromFlushRace() {
        UserCharacter character = representativeCharacter("오마");
        given(userCharacterQueryRepository.findRepresentativeByUserId(USER_ID))
                .willReturn(Optional.of(character));
        given(userCharacterQueryRepository.existsRepresentativeByNicknameExcludingId(
                "새이름", CHARACTER_ID
        )).willReturn(false);
        given(userCharacterWriteRepository.flushRepresentative(character))
                .willThrow(new BusinessException(GamificationErrorCode.DUPLICATE_NICKNAME));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> characterGrowthService.changeRepresentativeNickname(USER_ID, "새이름")
        );

        assertSame(GamificationErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
    }

    @Test
    @DisplayName("대표 캐릭터가 없으면 닉네임을 변경할 수 없다")
    void rejectsWhenRepresentativeCharacterMissing() {
        given(userCharacterQueryRepository.findRepresentativeByUserId(USER_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> characterGrowthService.changeRepresentativeNickname(USER_ID, "새이름")
        );

        assertSame(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND, exception.getErrorCode());
    }

    private UserCharacter representativeCharacter(String nickname) {
        UserCharacter character = UserCharacter.representative(USER_ID, 1L, nickname, "original");
        ReflectionTestUtils.setField(character, "id", CHARACTER_ID);
        return character;
    }
}
