package site.omagotchi.learningservice.team.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이름 규칙(GR-21)이 도메인에서 어떻게 표현되는지 고정한다.
 *
 * <p>여기서 {@code BusinessException}이나 {@code TeamErrorCode}를 import하지 않는 것이 중요하다.
 * 도메인은 외부 오류 계약을 모르고 조건을 {@code boolean}으로만 표현하며,
 * 그것을 400으로 옮기는 책임은 {@code TeamService}에 있다.
 * 이 테스트에 오류 코드가 다시 등장한다면 계층이 되돌아간 것이다.</p>
 */
class TeamTest {

    @Test
    @DisplayName("앞뒤 공백 제거")
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(Team.normalizeName("    오마고치    ")).isEqualTo("오마고치");
    }

    @Test
    @DisplayName("null은 빈 문자열로 정규화된다")
    void normalizesNullToEmptyString() {
        assertThat(Team.normalizeName(null)).isEmpty();
    }

    @Test
    @DisplayName("정규화는 판정하지 않는다 — 규칙을 어긴 이름도 그대로 돌려준다")
    void normalizationDoesNotValidate() {
        String tooLong = "가".repeat(31);

        assertThat(Team.normalizeName(tooLong)).isEqualTo(tooLong);
    }

    @Test
    @DisplayName("공백만 있는 이름은 유효하지 않다")
    void blankNameIsInvalid() {
        assertThat(Team.isValidName(Team.normalizeName(" "))).isFalse();
    }

    @Test
    @DisplayName("30자 초과는 유효하지 않다")
    void nameOverThirtyCharsIsInvalid() {
        assertThat(Team.isValidName("가".repeat(31))).isFalse();
    }

    @Test
    @DisplayName("정규화 후 30자면 유효하다 — 원문의 공백은 길이에 포함되지 않는다")
    void nameIsValidAtThirtyCharsAfterNormalization() {
        String padded = " " + "가".repeat(30) + " ";

        assertThat(Team.isValidName(Team.normalizeName(padded))).isTrue();
    }

    @Test
    @DisplayName("선검증을 빠뜨린 생성은 업무 실패가 아니라 호출 계약 위반이다")
    void creationWithoutValidationIsContractViolationNotBusinessFailure() {
        assertThatThrownBy(() -> Team.create(1L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성 시 이름이 정규화되어 저장된다")
    void nameIsNormalizedOnCreation() {
        assertThat(Team.create(1L, "  오마고치  ").getName()).isEqualTo("오마고치");
    }
}
