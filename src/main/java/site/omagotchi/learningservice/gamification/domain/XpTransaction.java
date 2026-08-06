package site.omagotchi.learningservice.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "xp_transactions", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class XpTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_character_id", nullable = false, updatable = false)
    private Long userCharacterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private XpSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 80)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static XpTransaction create(
            UUID userId,
            Long userCharacterId,
            XpSourceType sourceType,
            String sourceId,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("EXP는 양수여야 합니다.");
        }
        XpTransaction transaction = new XpTransaction();
        transaction.userId = userId;
        transaction.userCharacterId = userCharacterId;
        transaction.sourceType = sourceType;
        transaction.sourceId = sourceId;
        transaction.amount = amount;
        return transaction;
    }
}
