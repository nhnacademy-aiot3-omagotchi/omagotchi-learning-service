package site.omagotchi.learningservice.study.presentation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import site.omagotchi.learningservice.study.application.StudyPatternQueryService;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.presentation.response.StudyPatternToolResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 조회 대상 사용자는 LLM이 채우는 @ToolParam이 아니라 서버가 넣은 ToolContext에서 꺼낸다
 * 이 통로가 끊기면 사용자 사칭이 가능해지므로 위임 대상 userId를 고정해 둔다
 * (ADR ai-assistant/0010 LLM이 채운 Tool 인자를 신뢰하지 않고 서버가 확정)
 */
@DisplayName("LLM이 호출하는 학습 패턴 조회 Tool")
@ExtendWith(MockitoExtension.class)
class StudyPatternToolsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private StudyPatternQueryService studyPatternQueryService;

    @InjectMocks
    private StudyPatternTools studyPatternTools;

    @Test
    @DisplayName("ToolContext의 userId로 조회하고 결과를 응답으로 변환한다")
    void queriesWithUserIdFromToolContextAndConvertsResult() {
        given(this.studyPatternQueryService.getPattern(USER_ID, 7))
                .willReturn(StudyPatternResult.noData(7));

        StudyPatternToolResponse response =
                this.studyPatternTools.getStudyPattern(7, toolContextOf(USER_ID));

        verify(this.studyPatternQueryService).getPattern(USER_ID, 7);
        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.periodDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("기간을 지정하지 않으면 null을 그대로 넘겨 서버가 기본값을 정하게 한다")
    void passesNullPeriodSoServerDecidesDefault() {
        given(this.studyPatternQueryService.getPattern(USER_ID, null))
                .willReturn(StudyPatternResult.noData(30));

        this.studyPatternTools.getStudyPattern(null, toolContextOf(USER_ID));

        verify(this.studyPatternQueryService).getPattern(USER_ID, null);
    }

    private ToolContext toolContextOf(UUID userId) {
        return new ToolContext(Map.of("userId", userId));
    }
}
