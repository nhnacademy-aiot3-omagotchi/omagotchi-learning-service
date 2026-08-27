가package site.omagotchi.learningservice.telegram.presentation;

import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@Component
public class TelegramWebhookAuthenticator {

    /** 요청마다 getBytes 하지 않도록 한 번만 변환해 둔다 */
    private final byte[] expectedSecret;

    public TelegramWebhookAuthenticator(TelegramProperties properties) {
        this.expectedSecret = properties.webhook().secret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean isTelegram(String secret) {
        if (Objects.isNull(secret)) {
            return false;
        }

        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }
}
