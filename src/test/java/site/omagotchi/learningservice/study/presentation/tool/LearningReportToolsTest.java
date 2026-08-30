package site.omagotchi.learningservice.study.presentation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import site.omagotchi.learningservice.study.application.LearningReportQueryService;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;
import site.omagotchi.learningservice.study.presentation.response.LearningReportToolResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 리포트는 이번 기간(상위권 비교 포함)과 직전 기간을 함께 담는다
 * 기본 기간이 7일이라 패턴 조회 Tool(30일)과 다르지만, 그 확정은 서버가 하므로 Tool은 null을 그대로 넘긴다
 * (ADR ai-assistant/0010 LLM이 채운 Tool 인자를 신뢰하지 않고 서버가 확정)
 */
@DisplayName("LLM이 호출하는 학습 리포트 Tool")
@ExtendWith(MockitoExtension.class)
class LearningReportToolsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private LearningReportQueryService learningReportQueryService;

    @InjectMocks
    private LearningReportTools learningReportTools;

    @Test
    @DisplayName("ToolContext의 userId로 조회하고 이번 기간과 직전 기간을 함께 돌려준다")
    void queriesWithUserIdFromToolContextAndCarriesBothPeriods() {
        given(this.learningReportQueryService.getReport(USER_ID, 14))
                .willReturn(new LearningReportResult(
                        14, 300, 5, TopLearnerPatternResult.noData(14, 12)));

        LearningReportToolResponse response =
                this.learningReportTools.getLearningReport(14, toolContextOf(USER_ID));

        verify(this.learningReportQueryService).getReport(USER_ID, 14);
        assertThat(response.periodDays()).isEqualTo(14);
        assertThat(response.previousTotalStudyMinutes()).isEqualTo(300);
        assertThat(response.previousStudyDayCount()).isEqualTo(5);
        assertThat(response.thisPeriod().status()).isEqualTo("NO_DATA");
    }

    @Test
    @DisplayName("기간을 지정하지 않으면 null을 그대로 넘겨 서버가 리포트 기본값(7일)을 정하게 한다")
    void passesNullPeriodSoServerDecidesReportDefault() {
        given(this.learningReportQueryService.getReport(USER_ID, null))
                .willReturn(new LearningReportResult(
                        7, 0, 0, TopLearnerPatternResult.noData(7, 12)));

        LearningReportToolResponse response =
                this.learningReportTools.getLearningReport(null, toolContextOf(USER_ID));

        verify(this.learningReportQueryService).getReport(USER_ID, null);
        assertThat(response.periodDays()).isEqualTo(7);
    }

    private ToolContext toolContextOf(UUID userId) {
        return new ToolContext(Map.of("userId", userId));
    }
}
