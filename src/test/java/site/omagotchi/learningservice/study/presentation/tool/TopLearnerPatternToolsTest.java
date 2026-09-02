package site.omagotchi.learningservice.study.presentation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import site.omagotchi.learningservice.study.application.TopLearnerPatternQueryService;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;
import site.omagotchi.learningservice.study.presentation.response.TopLearnerPatternToolResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 상위권 비교는 타인의 학습 기록을 집계한 값을 다루므로, 조회 주체가 서버가 넣은 값으로 고정되어야 한다
 * 익명성 하한에 걸린 응답이 개인 식별 값 없이 그대로 LLM에게 전달되는지도 함께 본다
 * (ADR ai-assistant/0009, ai-assistant/0010)
 */
@DisplayName("LLM이 호출하는 상위권 패턴 비교 Tool")
@ExtendWith(MockitoExtension.class)
class TopLearnerPatternToolsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private TopLearnerPatternQueryService topLearnerPatternQueryService;

    @InjectMocks
    private TopLearnerPatternTools topLearnerPatternTools;

    @Test
    @DisplayName("ToolContext의 userId로 조회하고 결과를 응답으로 변환한다")
    void queriesWithUserIdFromToolContextAndConvertsResult() {
        given(this.topLearnerPatternQueryService.getTopLearnerPattern(USER_ID, 30))
                .willReturn(TopLearnerPatternResult.noData(30, 12));

        TopLearnerPatternToolResponse response =
                this.topLearnerPatternTools.getTopLearnerPattern(30, toolContextOf(USER_ID));

        verify(this.topLearnerPatternQueryService).getTopLearnerPattern(USER_ID, 30);
        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.cohortStudentCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("익명성 하한에 걸리면 표본 크기만 전달하고 집계값은 비운 채 내려준다")
    void carriesNoAggregateWhenSampleIsInsufficient() {
        given(this.topLearnerPatternQueryService.getTopLearnerPattern(USER_ID, null))
                .willReturn(TopLearnerPatternResult.insufficientSample(30, 5));

        TopLearnerPatternToolResponse response =
                this.topLearnerPatternTools.getTopLearnerPattern(null, toolContextOf(USER_ID));

        assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
        assertThat(response.cohortStudentCount()).isEqualTo(5);
        assertThat(response.topGroupSize()).isZero();
        assertThat(response.topAverageDailyMinutes()).isZero();
        assertThat(response.topFocusDensityPercent()).isZero();
        assertThat(response.topTypicalStartTime()).isNull();
        assertThat(response.myPattern()).isNull();
    }

    private ToolContext toolContextOf(UUID userId) {
        return new ToolContext(Map.of("userId", userId));
    }
}
