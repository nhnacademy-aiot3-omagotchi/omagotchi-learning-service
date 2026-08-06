package site.omagotchi.learningservice.gamification.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.gamification.application.result.CharacterGrowthResult;
import site.omagotchi.learningservice.gamification.application.result.HomeResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("게이미피케이션 홈 응답")
class HomeResponseTest {

    @Test
    @DisplayName("홈 캐릭터 표시 이름은 nickname을 사용한다")
    void usesNicknameAsDisplayName() {
        HomeResponse response = HomeResponse.from(new HomeResult(
                new CharacterGrowthResult(
                        1L,
                        "야간반장",
                        "야간반장",
                        0,
                        1,
                        0,
                        100,
                        AdvancementStage.BASE
                ),
                List.of()
        ));

        assertEquals("야간반장", response.growth().displayName());
    }
}
