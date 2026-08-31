package site.omagotchi.learningservice.cohort.infrastructure;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeConstraintTranslatorTest {

    @Test
    @DisplayName("기수별 ACTIVE 코드 유니크 충돌을 이미 존재 오류로 변환한다")
    void translatesActiveCohortConstraintViolation() {
        DataIntegrityViolationException original = violation(
                JoinCodeConstraintTranslator.UQ_ACTIVE_COHORT
        );

        RuntimeException translated = JoinCodeConstraintTranslator.translate(original);

        assertThat(translated).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) translated).getErrorCode())
                .isEqualTo(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS);
        assertThat(translated).hasCause(original);
    }

    @Test
    @DisplayName("알 수 없는 제약 위반은 원본 예외를 유지한다")
    void returnsOriginalForUnknownConstraint() {
        DataIntegrityViolationException original = violation("uq_other_constraint");

        assertThat(JoinCodeConstraintTranslator.translate(original)).isSameAs(original);
    }

    @Test
    @DisplayName("제약 이름을 읽을 수 없으면 원본 예외를 유지한다")
    void returnsOriginalWhenConstraintNameIsUnavailable() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("무결성 제약 위반");

        assertThat(JoinCodeConstraintTranslator.translate(original)).isSameAs(original);
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
