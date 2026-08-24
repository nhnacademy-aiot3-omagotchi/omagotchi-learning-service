package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("사용자 닉네임 검증")
class CharacterNicknameValidatorTest {

    @Test
    @DisplayName("닉네임은 필수값이다")
    void requiresNickname() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNicknameValidator.normalize(null));
    }

    @Test
    @DisplayName("trim 후 빈 닉네임은 허용하지 않는다")
    void rejectsBlankAfterTrim() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNicknameValidator.normalize("   "));
    }

    @Test
    @DisplayName("12자를 초과하는 닉네임은 허용하지 않는다")
    void rejectsNicknameOver30Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("가".repeat(13))
        );
    }

    @Test
    @DisplayName("특수 문자는 허용하지 않는다")
    void rejectsSpecialCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("오마_고치")
        );
    }

    @Test
    @DisplayName("숫자와 반복 문자로 변형한 금칙어도 허용하지 않는다")
    void rejectsObfuscatedForbiddenNickname() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("시1발")
        );
    }

    @Test
    @DisplayName("전각 문자를 정규화한 뒤 금칙어를 검사한다")
    void rejectsUnicodeObfuscatedForbiddenNickname() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("ｆｕｃｋ")
        );
    }

    @Test
    @DisplayName("운영 주체를 사칭하는 닉네임은 허용하지 않는다")
    void rejectsReservedNickname() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize("관리자")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ㅂㅗㅈㅣ",
            "ㄱㅐㅅㅐㄲl",
            "18년",
            "가슴주물럭",
            "니애뷔",
            "쉬이발",
            "트랜스젠더",
            "fuckyou",
            "공지사항"
    })
    @DisplayName("금칙어 목록의 자모·철자·문구 변형을 차단한다")
    void rejectsExpandedForbiddenNicknames(String nickname) {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNicknameValidator.normalize(nickname)
        );
    }

    @Test
    @DisplayName("정상 닉네임은 trim 후 저장한다")
    void normalizesNickname() {
        String nickname = CharacterNicknameValidator.normalize("  야간반장  ");

        assertEquals("야간반장", nickname);
    }
}
