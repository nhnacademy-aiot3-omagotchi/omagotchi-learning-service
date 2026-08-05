package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 전직 계산
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvancementCalculator {

    public static AdvancementStage calculate(int level) {
        if (level >= 30) {
            return AdvancementStage.THIRD;
        }
        if (level >= 20) {
            return AdvancementStage.SECOND;
        }
        if (level >= 10) {
            return AdvancementStage.FIRST;
        }
        return AdvancementStage.BASE;
    }
}
