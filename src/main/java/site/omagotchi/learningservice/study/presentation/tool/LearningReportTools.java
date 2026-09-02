package site.omagotchi.learningservice.study.presentation.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.ai.AiToolProvider;
import site.omagotchi.learningservice.study.application.LearningReportQueryService;
import site.omagotchi.learningservice.study.application.result.LearningReportResult;
import site.omagotchi.learningservice.study.presentation.response.LearningReportToolResponse;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class LearningReportTools implements AiToolProvider {

    private final LearningReportQueryService learningReportQueryService;

    @Tool(description = """
            사용자의 기간 학습 리포트 재료를 한 번에 조회합니다.
            "이번 주 리포트 만들어줘", "이번 주 공부 정리해줘", "지난주랑 비교해서 어때?" 처럼 한 기간의 학습을 종합 정리해 달라는 요청에 사용하세요.
            오늘·최근 기간의 학습 시간만 묻는 단일 질문에는 학습 시간 요약 도구를, 상위권과의 비교만 묻는 질문에는 상위권 학습 패턴 조회 도구를 사용하세요.
            
            이 도구의 응답에는 내 학습 패턴, 같은 기수 상위권과의 비교, 직전 같은 길이 기간과의 비교가 모두 포함됩니다.
            사용자 요청을 이 도구만으로 충족할 수 있으면 학습 시간 요약, 학습 패턴 조회, 상위권 학습 패턴 조회 도구를 함께 호출하지 마세요.
            
            ### periodDays 파라미터 작성 규칙
            - 사용자가 기간을 말했을 때만 넣으세요 (예: "한 달 리포트" → 30).
            - 기간을 말하지 않으면 넣지 마세요. 리포트 기본값 7일(주간)로 조회합니다.
            - 1~90 범위만 허용됩니다.
            
            ### 응답 구조
            - thisPeriod.myPattern: 이번 기간의 내 패턴 (몰입 밀도 focusDensityPercent 포함)
            - thisPeriod의 top으로 시작하는 값들: 같은 기수 상위권(총 공부 시간 기준)의 익명 평균
            - previousTotalStudyMinutes, previousStudyDayCount: 직전 같은 길이 구간의 나
            - environment: 세션을 "어디서 했는지"와 "그때 공기가 어땠는지"로 묶어 본 결과

            ### 리포트 작성 규칙
            - 다음 순서로 짧게 작성하세요:
              ① 이번 기간 요약 한 줄
              ② 잘한 점 하나
              ③ 직전 기간과 비교한 변화 (늘었는지 줄었는지, 대략 몇 % 인지)
              ④ 상위권과 가장 차이 나는 것 하나 (공부한 날 기준 일평균 학습량과
              몰입 밀도를 함께 보고 고르세요)
              ⑤ 환경 요인 (아래 규칙)
              ⑥ 다음 기간의 수행목표 하나.
            - 수행목표는 "합격하기" 같은 결과가 아니라 "매일 9시에 90분 블록 시작"처럼
              실행할 행동으로 제안하세요.
            - thisPeriod의 status가 INSUFFICIENT_SAMPLE이면 상위권 비교(④)는 빼고 작성하고,
              myPattern의 status가 NO_DATA면 기록이 없다고 알린 뒤 시작을 권유하세요.
            - 응답에 없는 값은 지어내지 마세요.

            ### 환경 요인(environment) 해석 규칙
            - environment는 세션을 "시간대 × 공간" 블록으로 쪼갠 결과입니다.
              timeBands는 시간대별(새벽·아침/오전/오후/저녁·밤), spaces는 공간별 묶음입니다.
            - 각 묶음의 densityPercent는 "그 시간 자리에 있던 시간 중 실제로 공부한 비율"입니다
              (studyMinutes ÷ spanMinutes). 낮으면 앉아는 있었지만 공부가 끊겼다는 뜻입니다.
            - 핵심은 **밀도가 가장 낮은 블록 하나를 찾아, 그 블록의 세 환경값(CO2·온도·습도)
              중 아래 판단선을 벗어난 것을 짚는 것**입니다. CO2만 보지 마세요.
              둘 이상 벗어났으면 조합으로 설명하세요(예: 더위와 습도가 겹쳐 졸음).
              예1: "오후 밀도가 42%로 가장 낮았고, 그때 CO2가 1,400ppm까지 올라 있었어요."
              예2: "저녁 밀도가 51%였는데 온도 27도에 습도 68%로 졸리기 쉬운 조건이었습니다."
              모든 블록과 모든 값을 나열하지 마세요.
            - 밀도 차이가 작거나 환경값이 모두 쾌적 구간이면 환경 탓으로 돌리지 말고
              "환경과 뚜렷한 관련은 보이지 않는다"고 말하세요.
            - sessionCount가 1인 블록은 밀도가 100%로 나옵니다. 쉬지 않고 한 번에 했다는 뜻이지
              특별히 잘했다는 뜻이 아니니 근거로 쓰지 마세요.
            - spaceSource가 "COHORT_LAB"이면 공간이 기록된 게 아니라 **기수 실습실로 추정**한
              것입니다. "실습실 기준으로 보면"처럼 추정임을 밝히고 단정하지 마세요.
              "PRESENCE"면 실제 체류 기록이라 그대로 말해도 됩니다.
            - **상관이지 인과가 아닙니다.** "CO2가 높아서 집중을 못했다"라고 단정하지 말고,
              "집중이 떨어진 시간대에 CO2가 높았다", "~였을 가능성이 있다"처럼 말하세요.
            - unknownSpaceSessionCount가 analyzedSessionCount보다 크면 "일부 세션만 분석했다"고
              밝히세요.
            - status가 "NO_SPACE_DATA"면 공간을 알 수 없어 분석을 못 한 것입니다. 오류가 아니니
              환경 절(⑤)은 생략하세요. "NO_SENSOR_DATA"면 공간은 알지만 측정값이 없는 경우로,
              밀도 블록만 말하고 환경값은 언급하지 마세요.

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
    public LearningReportToolResponse getLearningReport(
            @ToolParam(required = false,
                    description = "리포트 기간(일). 1~90. 사용자가 기간을 말하지 않으면 생략한다")
            Integer periodDays,
            ToolContext toolContext
    ) {
        UUID userId = (UUID) toolContext.getContext().get("userId");
        // 명세(로깅과 개인정보): userId는 로그에 남기지 않는다
        log.info("[LearningReportTools] 학습 리포트 조회 - periodDays = {}", periodDays);

        LearningReportResult result = learningReportQueryService.getReport(userId, periodDays);
        return LearningReportToolResponse.from(result);
    }
}
