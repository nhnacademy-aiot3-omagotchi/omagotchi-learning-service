package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JoinCodeConstraintTranslator {

    // V1__init_cohorts.sql의 기수별 ACTIVE 가입 코드 부분 유니크 인덱스명
    static final String UQ_ACTIVE_COHORT = "uq_cohort_join_codes_active_cohort";

    /**
     * 기수별 ACTIVE 코드 충돌만 업무 예외로 변환한다.
     * 다른 무결성 위반은 원본을 유지해 잘못된 409 응답으로 원인을 숨기지 않는다.
     */
    public static RuntimeException translate(DataIntegrityViolationException exception) {
        String constraintName = extractConstraintName(exception);
        if (constraintName != null && UQ_ACTIVE_COHORT.equalsIgnoreCase(constraintName)) {
            return new BusinessException(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS, exception);
        }
        return exception;
    }

    private static String extractConstraintName(DataIntegrityViolationException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ConstraintViolationException violation) {
            return violation.getConstraintName();
        }
        return null;
    }
}
