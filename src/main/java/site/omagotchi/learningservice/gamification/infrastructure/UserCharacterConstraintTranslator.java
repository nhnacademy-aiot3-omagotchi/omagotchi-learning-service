package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserCharacterConstraintTranslator {

    // V15__create_unique_user_nickname_index.sql 의 부분 유니크 인덱스명
    private static final String UX_REPRESENTATIVE_NICKNAME =
            "ux_user_characters_representative_nickname";

    /**
     * 던지지 않고 반환한다. 호출부에서 {@code throw translate(e)}로 쓰면
     * 컴파일러가 그 지점에서 흐름이 끝난다는 것을 안다.
     *
     * <p>인덱스명을 못 읽으면 원본을 그대로 돌려준다. 엉뚱한 409로 오진하면 조용히 묻히지만,
     * 500이 나가면 stack trace가 남아 원인을 찾을 수 있다.
     *
     * @param exception 원인이 Hibernate {@code ConstraintViolationException}이어야 인덱스명을 읽을 수 있다
     * @return 던질 준비가 된 예외. 이 메서드는 예외를 던지지 않는다
     */
    public static RuntimeException translate(DataIntegrityViolationException exception) {
        String name = extractConstraintName(exception);
        if (name == null) {
            return exception;
        }
        if (UX_REPRESENTATIVE_NICKNAME.equalsIgnoreCase(name)) {
            return new BusinessException(GamificationErrorCode.DUPLICATE_NICKNAME, exception);
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
