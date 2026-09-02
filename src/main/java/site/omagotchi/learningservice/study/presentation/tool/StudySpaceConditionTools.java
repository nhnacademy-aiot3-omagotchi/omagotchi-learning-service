package site.omagotchi.learningservice.study.presentation.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.StudySpaceConditionQueryService;
import site.omagotchi.learningservice.study.application.result.StudySpaceConditionResult;
import site.omagotchi.learningservice.study.presentation.response.StudySpaceConditionToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StudySpaceConditionTools implements AiToolProvider {

    private final StudySpaceConditionQueryService studySpaceConditionQueryService;

    @Tool(description = """
            지금 공부하기 좋은 공간을 고르기 위한, 기수 공간들의 현재 환경 상태를 조회합니다.
            "지금 공부하기 좋은 데 어디야?", "어디서 공부할까?", "지금 여기 공기 어때?" 처럼
            현재 시점의 공간 선택이나 환경을 묻는 질문에 사용하세요.
            지난 기간의 학습 분석에는 이 도구가 아니라 학습 리포트 도구를 쓰세요.

            ### 응답 해석 규칙
            - spaces는 이산화탄소가 낮은(환기가 잘 된) 순서로 정렬돼 있습니다. 다만 정렬은
              참고일 뿐이고, 추천은 co2·temperature·humidity 셋을 함께 보고 판단하세요.
              예를 들어 CO2가 조금 더 높아도 온·습도가 훨씬 쾌적하면 그쪽이 나을 수 있습니다.
            - 각 값은 가장 최근 시간대(measuredAt)의 공간 평균입니다. "지금 이 순간"이 아니라
              "최근 한 시간의 평균"이라는 뜻이니 그렇게 전달하세요.
            - usageStatus가 OCCUPIED면 지금 누군가 쓰고 있다는 뜻입니다. 추천할 때 함께
              알려주되, 누가 쓰는지는 알 수 없고 물어봐도 답하지 마세요.
            - status가 "NO_SPACE"면: 기수에 배정된 사용 가능한 공간이 없습니다.
            - status가 "NO_SENSOR_DATA"면: 공간은 있지만 최근 측정값이 없습니다. 환경 비교는
              할 수 없다고 알리고, 공간 목록만 안내하세요.
            - 값이 없는(null) 항목은 없다고만 하고 추측하지 마세요.
            - 환기·냉난방처럼 사용자가 바로 할 수 있는 행동을 한 가지 덧붙이면 좋습니다.

            ### 환경값 해석 기준 (연구·국내 기준에 근거한 판단선)
            - 이 기준은 판단에만 쓰고, 사용자에게 근거나 논문을 나열하지 마세요.
              "CO2 1,180ppm이라 환기가 필요해 보여요"처럼 자연스럽게 녹여 말하면 됩니다.
            - 이산화탄소(co2, ppm): 800 이하면 쾌적, 1,000 초과면 환기 부족(국내 교실 유지기준
              1,000ppm), 1,400 이상이면 판단력·집중이 뚜렷이 떨어지는 구간으로 봅니다.
              실내 CO2는 그 자체보다 "환기가 안 됐다"는 신호로 해석하는 것이 안전합니다.
            - 온도(temperature, 섭씨): 20~22도 부근이 학습에 가장 유리하고, 25도를 넘으면
              수행이 떨어지기 시작해 27도 이상에서는 뚜렷하게 나빠집니다. 18도 미만은 추워서
              집중이 흐트러집니다(국내 교실 기준 18~28도).
            - 습도(humidity, %): 40~60%가 쾌적 구간입니다(국내 교실 기준 30~80%).
              30% 미만은 건조해 불편하고, 60~70%를 넘으면 답답함과 졸음이 늘어납니다.
            - 조합이 중요합니다. 높은 온도와 높은 습도가 겹치면 졸음이 가장 심해지고,
              여기에 CO2까지 높으면 "공부는 했는데 머리가 안 돌아가는" 상태가 되기 쉽습니다.
              값 하나만 보지 말고 셋을 함께 읽으세요.
            """)
    public StudySpaceConditionToolResponse getStudySpaceCondition(ToolContext toolContext) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        // 명세(로깅과 개인정보): userId는 로그에 남기지 않는다
        log.info("[StudySpaceConditionTools] 공간 환경 상태 조회");

        StudySpaceConditionResult result = studySpaceConditionQueryService.getCurrentConditions(userId);
        return StudySpaceConditionToolResponse.from(result);
    }
}
