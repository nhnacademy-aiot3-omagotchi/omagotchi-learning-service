package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("레벨 계산")
class LevelCalculatorTest {

    @Test
    @DisplayName("레벨 정책의 경계값으로 현재 레벨을 계산한다")
    void calculatesLevelByPolicyBoundary() {
        List<LevelPolicy> policies = List.of(
                LevelPolicy.create(1, 0),
                LevelPolicy.create(2, 100),
                LevelPolicy.create(3, 250)
        );

        assertAll(
                () -> assertEquals(1, LevelCalculator.calculate(99, policies).level()),
                () -> assertEquals(2, LevelCalculator.calculate(100, policies).level()),
                () -> assertEquals(2, LevelCalculator.calculate(249, policies).level()),
                () -> assertEquals(3, LevelCalculator.calculate(250, policies).level())
        );
    }

    @Test
    @DisplayName("Lv10, Lv20, Lv30에서 전직 단계를 자동 계산한다")
    void calculatesAdvancementStage() {
        assertAll(
                () -> assertEquals(AdvancementStage.BASE, AdvancementCalculator.calculate(9)),
                () -> assertEquals(AdvancementStage.FIRST, AdvancementCalculator.calculate(10)),
                () -> assertEquals(AdvancementStage.SECOND, AdvancementCalculator.calculate(20)),
                () -> assertEquals(AdvancementStage.THIRD, AdvancementCalculator.calculate(30))
        );
    }
}
