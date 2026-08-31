package site.omagotchi.learningservice.telegram.infrastructure.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 연동 유니크 위반의 변환 규칙을 고정한다.
 *
 * <p>이 경로는 동시 요청 경합에서만 탄다. Service가 미리 소유권을 확인하므로 평소에는
 * 여기까지 오지 않고, 그래서 <b>잘못 변환해도 늦게 발견된다.</b></p>
 */
class TelegramConstraintTranslatorTest {

    @ParameterizedTest(name = "{0}")
    @DisplayName("남의 텔레그램 계정이면 이미 연결됨 오류로 옮긴다.")
    @ValueSource(strings = {
            "uq_telegram_user_links_chat",
            "uq_telegram_user_links_telegram_user"
    })
    void ownershipViolationBecomesAlreadyLinked(String constraint) {
        RuntimeException translated = TelegramConstraintTranslator.translate(violation(constraint));

        assertThat(translated)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED));
    }

    /**
     * 같은 계정에 연동 행을 두 번 만들려 한 것이라 <b>우리 읽기-쓰기 경합</b>이다.
     * 409로 덮으면 "이미 다른 사용자와 연결"이라는 틀린 안내가 나가고 원인도 묻힌다.
     */
    @Test
    @DisplayName("같은 계정 중복은 사용자 오류로 바꾸지 않는다.")
    void ownUserViolationStaysUnwrapped() {
        DataIntegrityViolationException original = violation("uq_telegram_user_links_user");

        assertThat(TelegramConstraintTranslator.translate(original))
                .isSameAs(original)
                .isNotInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("모르는 무결성 위반은 감싸지 않고 그대로 전파한다.")
    void propagatesUnknownViolationUnwrapped() {
        DataIntegrityViolationException original = violation("ck_telegram_user_links_disconnected");

        assertThat(TelegramConstraintTranslator.translate(original)).isSameAs(original);
    }

    /**
     * 원인이 Hibernate 예외가 아니면 제약명을 읽을 수 없다. 추측하지 않고 원본을 돌려준다.
     */
    @Test
    @DisplayName("제약명을 읽을 수 없으면 원본을 그대로 돌려준다.")
    void propagatesWhenConstraintNameIsUnreadable() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("원인을 알 수 없는 무결성 위반");

        assertThat(TelegramConstraintTranslator.translate(original)).isSameAs(original);
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "중복 키 위반",
                new ConstraintViolationException(
                        "duplicate key value violates unique constraint",
                        new SQLException("duplicate key", "23505"),
                        constraintName
                )
        );
    }
}
