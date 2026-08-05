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

@Entity
@Table(name = "advancement_histories", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AdvancementHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_character_id", nullable = false, updatable = false)
    private Long userCharacterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private AdvancementStage stage;

    @Column(nullable = false, updatable = false)
    private int level;

    @Column(name = "xp_transaction_id", updatable = false)
    private Long xpTransactionId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AdvancementHistory create(
            Long userCharacterId,
            AdvancementStage stage,
            int level,
            Long xpTransactionId
    ) {
        AdvancementHistory history = new AdvancementHistory();
        history.userCharacterId = userCharacterId;
        history.stage = stage;
        history.level = level;
        history.xpTransactionId = xpTransactionId;
        return history;
    }
}
