package site.omagotchi.learningservice.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quest_templates", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestType type;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "reward_xp", nullable = false)
    private long rewardXp;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static QuestTemplate create(
            QuestType type,
            String code,
            String title,
            int targetCount,
            long rewardXp,
            int displayOrder
    ) {
        QuestTemplate template = new QuestTemplate();
        template.type = type;
        template.code = code;
        template.title = title;
        template.targetCount = targetCount;
        template.rewardXp = rewardXp;
        template.active = true;
        template.displayOrder = displayOrder;
        return template;
    }
}
