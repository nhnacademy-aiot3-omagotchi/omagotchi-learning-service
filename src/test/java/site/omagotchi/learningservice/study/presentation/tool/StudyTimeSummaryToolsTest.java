package site.omagotchi.learningservice.study.presentation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import site.omagotchi.learningservice.study.application.StudyTimeSummaryQueryService;
import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;
import site.omagotchi.learningservice.study.presentation.response.StudyTimeSummaryToolResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("LLM이 호출하는 학습 시간 요약 Tool")
@ExtendWith(MockitoExtension.class)
class StudyTimeSummaryToolsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private StudyTimeSummaryQueryService studyTimeSummaryQueryService;

    @InjectMocks
    private StudyTimeSummaryTools studyTimeSummaryTools;

    @Test
    @DisplayName("ToolContext의 userId로 조회하고 결과를 응답으로 변환한다")
    void queriesWithUserIdFromToolContextAndConvertsResult() {
        given(studyTimeSummaryQueryService.getSummary(USER_ID, 7))
                .willReturn(new StudyTimeSummaryResult(
                        StudyTimeSummaryResult.Status.OK, 7, 420, 5, 84));

        StudyTimeSummaryToolResponse response =
                studyTimeSummaryTools.getStudyTimeSummary(7, toolContextOf(USER_ID));

        verify(studyTimeSummaryQueryService).getSummary(USER_ID, 7);
        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.totalStudyMinutes()).isEqualTo(420);
        assertThat(response.averageStudyMinutesPerStudyDay()).isEqualTo(84);
    }

    @Test
    @DisplayName("기간을 지정하지 않으면 null을 그대로 넘겨 서버가 기본값을 정하게 한다")
    void passesNullPeriodSoServerDecidesDefault() {
        given(studyTimeSummaryQueryService.getSummary(USER_ID, null))
                .willReturn(StudyTimeSummaryResult.noData(7));

        StudyTimeSummaryToolResponse response =
                studyTimeSummaryTools.getStudyTimeSummary(null, toolContextOf(USER_ID));

        verify(studyTimeSummaryQueryService).getSummary(USER_ID, null);
        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.periodDays()).isEqualTo(7);
    }

    private ToolContext toolContextOf(UUID userId) {
        return new ToolContext(Map.of("userId", userId));
    }
}
