package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("캐릭터 별명 검증")
class CharacterNicknameValidatorTest {

    @Test
    @DisplayName("캐릭터 별명은 필수값이다")
    void requiresNickname() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNicknameValidator.normalize(null));
    }

    @Test
    @DisplayName("trim 후 빈 별명은 허용하지 않는다")
    void rejectsBlankAfterTrim() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNicknameValidator.normalize("   "));
    }

    @Test
    @DisplayName("30자를 초과하는 별명은 허용하지 않는다")
    void rejectsNicknameOver30Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("가".repeat(31))
        );
    }

    @Test
    @DisplayName("정상 별명은 trim 후 저장한다")
    void normalizesNickname() {
        String nickname = CharacterNicknameValidator.normalize("  야간반장  ");

        assertEquals("야간반장", nickname);
    }
}
