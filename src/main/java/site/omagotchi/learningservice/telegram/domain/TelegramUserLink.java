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
@Table(name = "telegram_user_links", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramUserLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "notification_enabled", nullable = false)
    private Boolean notificationEnabled;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    @Column(name = "disconnected_at")
    private OffsetDateTime disconnectedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static TelegramUserLink link(Long userId, Long telegramUserId, Long telegramChatId) {
        OffsetDateTime now = OffsetDateTime.now();

        TelegramUserLink link = new TelegramUserLink();
        link.userId = userId;
        link.telegramUserId = telegramUserId;
        link.telegramChatId = telegramChatId;
        link.notificationEnabled = true;
        link.linkedAt = now;
        link.createdAt = now;
        link.updatedAt = now;
        return link;
    }

    public void reconnect(Long telegramUserId, Long telegramChatId) {
        OffsetDateTime now = OffsetDateTime.now();

        this.telegramUserId = telegramUserId;
        this.telegramChatId = telegramChatId;
        this.notificationEnabled = true;
        this.linkedAt = now;
        this.disconnectedAt = null;
        this.updatedAt = now;
    }

    public void changeNotificationEnabled(boolean enabled) {
        this.notificationEnabled = enabled;
        this.updatedAt = OffsetDateTime.now();
    }

    public void disconnect() {
        this.notificationEnabled = false;
        this.disconnectedAt = OffsetDateTime.now();
        this.updatedAt = this.disconnectedAt;
    }
}
