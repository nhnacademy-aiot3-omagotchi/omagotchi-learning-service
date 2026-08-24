package site.omagotchi.learningservice.gamification.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.sql.SQLException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인덱스명 → 오류 코드 변환.
 *
 * <p>이름의 정본은 {@code V15__create_unique_user_nickname_index.sql}이다.
 * 마이그레이션에서 인덱스명이 바뀌면 이 테스트가 먼저 깨져야 한다 — 안 깨지면
 * 운영에서 중복 닉네임이 409 대신 500으로 나간다.
 */
class UserCharacterConstraintTranslatorTest {

    private static final String UX_REPRESENTATIVE_NICKNAME =
            "ux_user_characters_representative_nickname";

    @Test
    @DisplayName("대표 닉네임 유니크 위반을 DUPLICATE_NICKNAME으로 변환한다.")
    void translatesRepresentativeNicknameViolation() {
        RuntimeException translated =
                UserCharacterConstraintTranslator.translate(violation(UX_REPRESENTATIVE_NICKNAME));

        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorCode())
                .isEqualTo(GamificationErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("알 수 없는 인덱스 위반은 원본 예외를 그대로 돌려준다.")
    void returnsOriginalExceptionForUnknownConstraint() {
        // 엉뚱한 409로 오진하면 조용히 묻히지만, 500은 stack trace가 남아 원인을 찾을 수 있다.
        DataIntegrityViolationException original = violation("uq_some_other_index");

        assertThat(UserCharacterConstraintTranslator.translate(original)).isSameAs(original);
    }

    @Test
    @DisplayName("인덱스명을 읽을 수 없으면 원본 예외를 그대로 돌려준다.")
    void returnsOriginalExceptionWhenConstraintNameUnreadable() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("원인을 알 수 없는 무결성 위반");

        assertThat(UserCharacterConstraintTranslator.translate(original)).isSameAs(original);
    }

    /** 원본을 cause로 달아야 예상 밖 실패의 스택 트레이스가 최종 경계까지 살아남는다. */
    @Test
    @DisplayName("변환한 예외는 원본을 cause로 보존한다.")
    void translatedExceptionPreservesOriginalAsCause() {
        DataIntegrityViolationException original = violation(UX_REPRESENTATIVE_NICKNAME);

        assertThat(UserCharacterConstraintTranslator.translate(original)).hasCause(original);
    }

    /**
     * 터키어 로케일에서는 {@code String#toLowerCase()}가 "I"를 "i"가 아니라 "ı"로 바꾼다.
     * {@code Locale.ROOT}를 쓰지 않으면 대문자 인덱스명 매칭이 깨져 중복 닉네임이 500으로 나간다.
     * JVM 기본 로케일을 바꾸는 테스트라 다른 테스트에 새지 않게 반드시 원래 값으로 되돌린다.
     */
    @Test
    @DisplayName("JVM 기본 로케일이 터키어여도 인덱스명을 정확히 인식한다.")
    void recognizesConstraintNameUnderTurkishLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.of("tr", "TR"));
        try {
            RuntimeException translated = UserCharacterConstraintTranslator.translate(
                    violation(UX_REPRESENTATIVE_NICKNAME.toUpperCase(Locale.ROOT))
            );

            assertThat(translated).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) translated).getErrorCode())
                    .isEqualTo(GamificationErrorCode.DUPLICATE_NICKNAME);
        } finally {
            Locale.setDefault(original);
        }
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
