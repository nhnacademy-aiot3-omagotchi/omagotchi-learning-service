package site.omagotchi.learningservice.gamification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "level_policies", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelPolicy {

    @Id
    @Column(nullable = false)
    private Integer level;

    @Column(name = "min_total_xp", nullable = false)
    private long minTotalXp;

    public static LevelPolicy create(int level, long minTotalXp) {
        LevelPolicy policy = new LevelPolicy();
        policy.level = level;
        policy.minTotalXp = minTotalXp;
        return policy;
    }
}
