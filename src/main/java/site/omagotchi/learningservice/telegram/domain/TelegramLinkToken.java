package site.omagotchi.learningservice.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "telegram_link_tokens", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static TelegramLinkToken issue(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Telegram 연결 토큰 만료 시각은 현재보다 이후여야 합니다.");
        }

        TelegramLinkToken token = new TelegramLinkToken();
        token.userId = userId;
        token.tokenHash = requireText(tokenHash, "tokenHash");
        token.expiresAt = expiresAt;
        token.createdAt = now;
        return token;
    }

    public boolean isUsableAt(OffsetDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
