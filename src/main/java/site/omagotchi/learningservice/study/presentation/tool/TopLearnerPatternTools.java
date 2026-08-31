package site.omagotchi.learningservice.study.presentation.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.TopLearnerPatternQueryService;
import site.omagotchi.learningservice.study.application.result.TopLearnerPatternResult;
import site.omagotchi.learningservice.study.presentation.response.TopLearnerPatternToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class TopLearnerPatternTools implements AiToolProvider {

    private final TopLearnerPatternQueryService topLearnerPatternQueryService;

    @Tool(description = """
            같은 기수 상위권 학습자들의 익명 통계와 사용자 본인의 학습 패턴을 함께 조회합니다.
            "잘하는 사람들은 어떻게 공부해?", "상위권이랑 나랑 뭐가 달라?", "1등처럼 공부하고 싶어" 처럼 다른 학습자와의 비교나 방향 제시가 필요할 때 사용하세요.
            본인 패턴만 궁금한 질문에는 이 도구가 아니라 학습 패턴 조회 도구를 사용하세요.
            
            ### periodDays 파라미터 작성 규칙
            - 사용자가 기간을 말했을 때만 넣으세요 (예: "이번 주" → 7, "한 달" → 30).
            - 기간을 말하지 않으면 넣지 마세요. 서버가 기본값 30일로 조회합니다.
            - 1~90 범위만 허용됩니다.
            
            ### 응답 해석 규칙
            - status가 "INSUFFICIENT_SAMPLE"이면: 기수 인원이 적어 익명성을 지키며 통계를 낼 수 없는 것입니다. 개인정보 보호를 위해 제공할 수 없다고 안내하세요.
            - status가 "NO_DATA"면: 그 기간에 기수의 학습 기록이 부족한 것입니다. 오류가 아닙니다.
            - top으로 시작하는 값들은 상위 그룹(topGroupSize명)의 익명 평균입니다.
              특정 개인의 기록이 아니며, 누가 상위권인지는 알 수 없고 답해서도 안 됩니다.
            - myPattern은 사용자 본인의 같은 기간 패턴입니다. top 값들과 비교해서 가장 차이가 큰 한두 가지(시작 시각, 세션 길이, 학습일 수)를 짚어 방향을 제안하세요.
            - 상위 그룹은 기간 내 총 공부 시간이 많은 순으로 뽑힙니다. 밀도가 높은 순이 아니므로 topFocusDensityPercent가 사용자보다 항상 높지는 않습니다.
            - 차이를 짚을 때는 총 학습량과 몰입 밀도를 함께 보세요. 총 시간이 비슷한데 밀도가 다르면, 시간을 늘리라고 하지 말고 밀도 차이를 짚으세요.
            - 비교는 방향 제시용입니다. 뒤처졌다고 비난하지 말고, 따라 해볼 행동 하나를 권하세요.
            """)
    public TopLearnerPatternToolResponse getTopLearnerPattern(
            @ToolParam(required = false,
                    description = "조회 기간(일). 1~90. 사용자가 기간을 말하지 않으면 생략한다")
            Integer periodDays,
            ToolContext toolContext
    ) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        // 명세(로깅과 개인정보): userId는 로그에 남기지 않는다
        log.info("[TopLearnerPatternTools] 상위권 패턴 조회 - periodDays = {}", periodDays);

        TopLearnerPatternResult result = topLearnerPatternQueryService
                .getTopLearnerPattern(userId, periodDays);
        return TopLearnerPatternToolResponse.from(result);
    }
}