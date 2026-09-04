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
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "telegram_user_links", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramUserLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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

    public static TelegramUserLink link(UUID userId, Long telegramUserId, Long telegramChatId) {
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

    /**
     * 지금 이 연동으로 알림을 보낼 수 있는지.
     */
    public boolean canReceiveNotification() {
        return Objects.isNull(disconnectedAt) && Boolean.TRUE.equals(notificationEnabled);
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
