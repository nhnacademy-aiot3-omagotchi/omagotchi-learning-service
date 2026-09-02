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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_daily_quests", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserDailyQuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "quest_date", nullable = false, updatable = false)
    private LocalDate questDate;

    @Column(name = "template_id", updatable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestType type;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "progress_count", nullable = false)
    private int progressCount;

    @Column(name = "reward_xp", nullable = false)
    private long rewardXp;

    // 학습 시간 퀘스트의 실제 목표(초). 횟수형 퀘스트는 null이다.
    @Column(name = "target_seconds")
    private Integer targetSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_source", nullable = false, length = 20)
    private QuestTargetSource targetSource;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Version
    @Column(nullable = false)
    private Short version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserDailyQuest fromTemplate(UUID userId, LocalDate questDate, QuestTemplate template) {
        return create(
                userId,
                questDate,
                template.getId(),
                template.getType(),
                template.getCode(),
                template.getTitle(),
                template.getTargetCount(),
                template.getRewardXp()
        );
    }

    public static UserDailyQuest create(
            UUID userId,
            LocalDate questDate,
            Long templateId,
            QuestType type,
            String code,
            String title,
            int targetCount,
            long rewardXp
    ) {
        UserDailyQuest quest = new UserDailyQuest();
        quest.userId = userId;
        quest.questDate = questDate;
        quest.templateId = templateId;
        quest.type = type;
        quest.code = code;
        quest.title = title;
        quest.targetCount = targetCount;
        quest.progressCount = 0;
        quest.rewardXp = rewardXp;
        quest.status = QuestStatus.IN_PROGRESS;
        quest.targetSource = QuestTargetSource.TEMPLATE;
        return quest;
    }

    /**
     * 예측 기반 학습 시간 퀘스트를 발급한다.
     *
     * <p>MVP는 진행도를 노출하지 않으므로 targetCount는 1로 두고 완료 판정만 목표 시간으로 한다.
     * 제목은 사용자마다 다르므로 템플릿 제목 대신 산정된 목표로 만든 문구를 복사한다.
     */
    public static UserDailyQuest studyTimeFromTemplate(
            UUID userId,
            LocalDate questDate,
            QuestTemplate template,
            String title,
            StudyTimeQuestTarget target
    ) {
        UserDailyQuest quest = create(
                userId,
                questDate,
                template.getId(),
                template.getType(),
                template.getCode(),
                title,
                1,
                template.getRewardXp()
        );
        quest.targetSeconds = target.targetSeconds();
        quest.targetSource = target.source();
        quest.modelVersion = target.modelVersion();
        return quest;
    }

    public boolean isStudyTimeQuest() {
        return targetSeconds != null;
    }

    public void progress(int amount, Instant completedAt) {
        if (amount <= 0 || status != QuestStatus.IN_PROGRESS) {
            return;
        }
        progressCount = Math.min(targetCount, progressCount + amount);
        if (progressCount >= targetCount) {
            complete(completedAt);
        }
    }

    public void complete(Instant completedAt) {
        if (status == QuestStatus.IN_PROGRESS) {
            progressCount = targetCount;
            status = QuestStatus.COMPLETED;
            this.completedAt = completedAt;
        }
    }

    public void claim(Instant claimedAt) {
        if (status != QuestStatus.COMPLETED) {
            throw new IllegalStateException("완료된 퀘스트만 보상을 받을 수 있습니다.");
        }
        status = QuestStatus.CLAIMED;
        this.claimedAt = claimedAt;
    }

    public void expire() {
        if (status == QuestStatus.IN_PROGRESS || status == QuestStatus.COMPLETED) {
            status = QuestStatus.EXPIRED;
        }
    }
}
