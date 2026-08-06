package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

/**
 * 레벨 계산 로직
 * 최대 레벨 30 고정
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LevelCalculator {

    public static final int MAX_LEVEL = 30;

    public static LevelState calculate(long totalXp, List<LevelPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("레벨 정책이 필요합니다.");
        }

        List<LevelPolicy> sortedPolicies = policies.stream()
                .sorted(Comparator.comparingInt(LevelPolicy::getLevel))
                .toList();

        LevelPolicy current = sortedPolicies.getFirst();
        for (LevelPolicy policy : sortedPolicies) {
            if (policy.getLevel() > MAX_LEVEL || policy.getMinTotalXp() > totalXp) {
                break;
            }
            current = policy;
        }

        int currentLevel = current.getLevel();
        long nextMinXp = sortedPolicies.stream()
                .filter(policy -> policy.getLevel() == currentLevel + 1)
                .map(LevelPolicy::getMinTotalXp)
                .findFirst()
                .orElse(current.getMinTotalXp());

        return new LevelState(
                currentLevel,
                totalXp,
                current.getMinTotalXp(),
                nextMinXp,
                AdvancementCalculator.calculate(currentLevel)
        );
    }
}
