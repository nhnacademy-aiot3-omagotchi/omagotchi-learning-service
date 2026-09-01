package site.omagotchi.learningservice.study.presentation;

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
            "이번 주 리포트 만들어줘", "이번 주 공부 정리해줘", "지난주랑 비교해서 어때?" 처럼
            한 기간의 학습을 종합 정리해 달라는 요청에 사용하세요.
            단일 질문(오늘 몇 시간 했어? / 상위권은 어때?)에는 이 도구 말고 다른 도구를 쓰세요.

            ### periodDays 파라미터 작성 규칙
            - 사용자가 기간을 말했을 때만 넣으세요 (예: "한 달 리포트" → 30).
            - 기간을 말하지 않으면 넣지 마세요. 리포트 기본값 7일(주간)로 조회합니다.
            - 1~90 범위만 허용됩니다.

            ### 응답 구조
            - thisPeriod.myPattern: 이번 기간의 내 패턴 (몰입 밀도 focusDensityPercent 포함)
            - thisPeriod의 top으로 시작하는 값들: 같은 기수 상위권의 익명 평균
            - previousTotalStudyMinutes, previousStudyDayCount: 직전 같은 길이 구간의 나
            - environment: 세션을 "어디서 했는지"와 "그때 공기가 어땠는지"로 묶어 본 결과

            ### 리포트 작성 규칙
            - 다음 순서로 짧게 작성하세요: ① 이번 기간 요약 한 줄 ② 잘한 점 하나
              ③ 직전 기간과 비교한 변화 (늘었는지 줄었는지, 대략 몇 % 인지)
              ④ 상위권과 가장 차이 나는 것 하나 — 몰입 밀도(focusDensityPercent) 차이를
              최우선으로 확인하세요 ⑤ 환경 요인 (아래 규칙) ⑥ 다음 기간의 수행목표 하나.
            - 수행목표는 "합격하기" 같은 결과가 아니라 "매일 9시에 90분 블록 시작"처럼
              실행할 행동으로 제안하세요.
            - thisPeriod의 status가 INSUFFICIENT_SAMPLE이면 상위권 비교(④)는 빼고 작성하고,
              myPattern의 status가 NO_DATA면 기록이 없다고 알린 뒤 시작을 권유하세요.
            - 응답에 없는 값은 지어내지 마세요.

            ### 환경 요인(environment) 해석 규칙
            - environment.contrasts가 이 절의 핵심입니다. 측정항목(co2·temperature·humidity)
              마다, 사용자의 세션을 그 값의 중앙값(medianValue)으로 갈라 값이 낮았던
              세션들(low~)과 높았던 세션들(high~)의 몰입 밀도를 비교한 결과입니다.
            - 세 항목을 모두 나열하지 말고, **밀도 차이가 가장 큰 항목 하나**를 골라 짚으세요.
              차이가 모두 작으면 "환경과 뚜렷한 관련은 보이지 않는다"고 말하면 됩니다.
            - 아래 판단선과 함께 읽으세요. 예를 들어 CO2가 높았던 세션의 평균이 1,400을
              넘으면서 밀도가 낮다면, 환기 부족이 원인일 가능성을 짚을 만합니다. 반대로 두
              그룹 모두 쾌적 구간이면 환경 탓으로 돌리지 마세요.
            - spaces는 공간별 성과입니다. 공간마다 밀도와 평균 환경값이 다르면 어느 공간이
              잘 맞았는지 알려주세요. 공간 이름은 그대로 쓰고, 다른 사용자 이야기는 하지 마세요.
            - **상관이지 인과가 아닙니다.** "CO2가 높아서 집중을 못했다"라고 단정하지 말고,
              "집중이 떨어진 세션에서 CO2가 높았다", "~였을 가능성이 있다"처럼 말하세요.
            - unknownSpaceSessionCount는 어디서 했는지 기록이 없는 세션 수입니다. 이 값이
              analyzedSessionCount보다 크면 "일부 세션만 분석했다"고 밝히세요.
            - status가 "NO_SPACE_DATA"면 공간 기록이 없어 환경 분석을 못 한 것입니다.
              오류가 아니니 환경 절(⑤)은 생략하고 나머지로 리포트를 작성하세요.
              "NO_SENSOR_DATA"면 공간은 알지만 그 시간대 측정값이 없는 경우로, 같게 처리하세요.

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
