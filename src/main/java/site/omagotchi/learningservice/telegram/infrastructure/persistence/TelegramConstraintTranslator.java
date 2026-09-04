package site.omagotchi.learningservice.telegram.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;

import java.util.Locale;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TelegramConstraintTranslator {

    private static final String UQ_CHAT = "uq_telegram_user_links_active_chat";
    private static final String UQ_TELEGRAM_USER = "uq_telegram_user_links_active_telegram_user";

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        String name = extractConstraintName(exception);

        if (name == null) {
            return exception;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains(UQ_CHAT) || normalized.contains(UQ_TELEGRAM_USER)) {
            return new BusinessException(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED, exception);
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
