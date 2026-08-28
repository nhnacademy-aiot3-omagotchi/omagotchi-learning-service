package site.omagotchi.learningservice.study.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.StudyPatternQueryService;
import site.omagotchi.learningservice.study.application.result.StudyPatternResult;
import site.omagotchi.learningservice.study.presentation.response.StudyPatternToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StudyPatternTools implements AiToolProvider {

    private final StudyPatternQueryService studyPatternQueryService;

    @Tool(description = """
            사용자의 최근 학습 패턴 요약을 조회합니다.
            "내 공부 습관 어때?", "요즘 나 공부 잘하고 있어?", "언제 공부하는 게 좋아?" 처럼
            학습 패턴의 진단이나 조언이 필요할 때 사용하세요.

            ### periodDays 파라미터 작성 규칙
            - 사용자가 기간을 말했을 때만 넣으세요 (예: "이번 주" → 7, "한 달" → 30).
            - 기간을 말하지 않으면 넣지 마세요. 서버가 기본값 14일로 조회합니다.
            - 1~90 범위만 허용됩니다. 범위를 벗어난 요청이면 90일까지만 가능하다고 안내하세요.

            ### 응답 해석 규칙
            - status가 "NO_DATA"면: 그 기간에 학습 기록이 없는 것입니다. 오류가 아니니
              첫 학습 시작을 권유하는 코칭으로 답하세요.
            - typicalStartTime: 이 사용자가 보통 하루의 공부를 시작하는 시각입니다.
              날마다 들쭉날쭉한지 일정한지는 알 수 없으니 "보통 이 무렵 시작"으로만 말하세요.
            - bestStartHour: 이 시각에 시작한 세션들의 누적 공부량이 가장 많았다는 뜻입니다.
              "이 시간대 내내 공부했다"가 아니라 "이 시각에 시작하면 잘 굴러간다"로 해석해서
              시작 시각을 추천할 때 사용하세요.
            - averageSessionMinutes가 짧고 sessionCount가 많으면 공부가 잘게 끊기고 있다는
              신호입니다. 몰입 블록을 만들라고 조언할 근거로 쓰세요.
            - 응답에 없는 값(과목, 장소, 집중도 등)은 지어내지 마세요.
            """)
    public StudyPatternToolResponse getStudyPattern(
            @ToolParam(required = false,
                    description = "조회 기간(일). 1~90. 사용자가 기간을 말하지 않으면 생략한다")
            Integer periodDays,
            ToolContext toolContext
    ) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        // 명세(로깅과 개인정보): userId는 로그에 남기지 않는다
        log.info("[StudyPatternTools] 학습 패턴 조회 - periodDays = {}", periodDays);

        StudyPatternResult result = studyPatternQueryService.getPattern(userId, periodDays);
        return StudyPatternToolResponse.from(result);
    }
}