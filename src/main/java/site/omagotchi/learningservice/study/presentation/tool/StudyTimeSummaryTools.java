package site.omagotchi.learningservice.study.presentation.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.StudyTimeSummaryQueryService;
import site.omagotchi.learningservice.study.application.result.StudyTimeSummaryResult;
import site.omagotchi.learningservice.study.presentation.response.StudyTimeSummaryToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StudyTimeSummaryTools implements AiToolProvider {

    private final StudyTimeSummaryQueryService studyTimeSummaryQueryService;

    @Tool(description = """
            사용자의 최근 저장된 학습 시간을 간단히 요약해 조회합니다.
            "오늘 몇 시간 공부했어?", "최근 일주일 동안 얼마나 공부했어?", "최근 30일 중 며칠 공부했어?"처럼 총 학습 시간이나 학습일 수만 궁금할 때 사용하세요.
            습관·세션·시작 시각·몰입 밀도 분석은 학습 패턴 조회 도구를 사용하고, 직전 기간 또는 상위권 비교가 필요한 종합 리포트는 학습 리포트 도구를 사용하세요.

            ### periodDays 파라미터 작성 규칙
            - "오늘"은 1, "최근 일주일" 또는 "최근 7일"은 7, "최근 한 달" 또는 "최근 30일"은 30을 넣으세요.
            - 기간을 말하지 않으면 넣지 마세요. 서버가 현재 집계일을 포함한 최근 7일로 조회합니다.
            - 1~90 범위만 허용됩니다. "지난주", "지난달"처럼 과거의 고정 기간은 지원하지 않습니다.

            ### 응답 해석 규칙
            - status가 "NO_DATA"면: 해당 기간에 저장된 학습 기록이 없는 것입니다. 오류가 아니니 첫 학습 시작을 권유하세요.
            - totalStudyMinutes는 기간 내 저장된 총 학습 시간(분)입니다. 실행 중인 타이머 시간은 포함하지 않습니다.
            - averageStudyMinutesPerStudyDay는 기간 전체 일수가 아니라 실제 공부한 날 기준 하루 평균(분)입니다.
            - 응답에 없는 값(세션 수, 시작 시각, 몰입 밀도 등)은 지어내지 마세요.
            """)
    public StudyTimeSummaryToolResponse getStudyTimeSummary(
            @ToolParam(required = false,
                    description = "최근 조회 기간(일). 오늘=1, 최근 일주일=7, 최근 한 달=30. 1~90만 허용한다")
            Integer periodDays,
            ToolContext toolContext
    ) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        log.info("[StudyTimeSummaryTools] 학습 시간 요약 조회 - periodDays = {}", periodDays);

        StudyTimeSummaryResult result = studyTimeSummaryQueryService.getSummary(userId, periodDays);
        return StudyTimeSummaryToolResponse.from(result);
    }
}
