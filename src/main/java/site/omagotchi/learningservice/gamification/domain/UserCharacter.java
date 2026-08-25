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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_characters", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "game_character_id", nullable = false, updatable = false)
    private Long gameCharacterId;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "is_representative", nullable = false)
    private boolean representative;

    @Column(name = "total_xp", nullable = false)
    private long totalXp;

    @Column(name = "level", nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(name = "advancement_stage", nullable = false, length = 20)
    private AdvancementStage advancementStage;

    @Column(name = "color_id", nullable = false, length = 30)
    private String colorId;

    @Version
    @Column(nullable = false)
    private Short version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserCharacter representative(
            UUID userId,
            Long gameCharacterId,
            String nickname,
            String colorId
    ) {
        UserCharacter character = new UserCharacter();
        character.userId = userId;
        character.gameCharacterId = gameCharacterId;
        character.nickname = CharacterNicknameValidator.normalize(nickname);
        character.representative = true;
        character.totalXp = 0;
        character.level = 1;
        character.advancementStage = AdvancementStage.BASE;
        character.colorId = CharacterAppearance.normalizeColorId(colorId);
        return character;
    }

    public String displayName() {
        return nickname;
    }

    public void updateNickname(String nickname) {
        this.nickname = CharacterNicknameValidator.normalize(nickname);
    }

    public LevelState addXp(long amount, List<LevelPolicy> policies) {
        if (amount <= 0) {
            throw new IllegalArgumentException("EXP는 양수여야 합니다.");
        }
        totalXp += amount;
        LevelState state = LevelCalculator.calculate(totalXp, policies);
        level = state.level();
        advancementStage = state.advancementStage();
        return state;
    }

    public LevelState levelState(List<LevelPolicy> policies) {
        return LevelCalculator.calculate(totalXp, policies);
    }
}
